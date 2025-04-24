package ru.nsu.fit.evdokimova.worker.service;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import ru.nsu.fit.evdokimova.worker.config.RabbitWorkerConfig;
import ru.nsu.fit.evdokimova.worker.model.dto.RequestFromManagerToWorker;
import org.paukov.combinatorics3.Generator;
import ru.nsu.fit.evdokimova.worker.model.dto.ResponseToManagerFromWorker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static ru.nsu.fit.evdokimova.worker.config.ConstantsWorker.ALPHABET;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitWorkerConfig.TASKS_QUEUE)
    public void processTask(RequestFromManagerToWorker request, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("Received task: requestId={}, partNumber={}, range: {}-{}",
                    request.getRequestId(), request.getPartNumber(),
                    request.getStartIndex(), request.getEndIndex());

            List<String> foundWords = findWords(request);
            sendResultToManager(request.getRequestId(), foundWords);

            channel.basicAck(tag, false); //тут подтверждаем только конкретноее соо с тегом
            log.info("Task processed and acknowledged: requestId={}, part={}",
                    request.getRequestId(), request.getPartNumber());
        } catch (Exception e) {
            log.error("Error processing task: requestId={}, part={}, error: {}",
                    request.getRequestId(), request.getPartNumber(), e.getMessage());

            channel.basicNack(tag, false, true);  // тут отказ от конкретного соо, кот. вернуть обработ в очередь надо
        }
    }

    private List<String> findWords(RequestFromManagerToWorker request) {
        List<String> foundWords = new ArrayList<>();
        String targetHash = request.getHash();

        List<String> words = generateWords(request.getMaxLength(),
                request.getStartIndex(),
                request.getEndIndex());

        log.info("Worker generated {} words", words.size());
        for (String word : words) {
            String calculatedHash = DigestUtils.md5Hex(word);
            if (calculatedHash.equals(targetHash)) {
                log.info("! Match found: {}", word);
                foundWords.add(word);
            }
        }
        return foundWords;
    }

    private List<String> generateWords(int maxLength, int startIndex, int endIndex) {
        return IntStream.rangeClosed(1, maxLength)
                .mapToObj(length ->
                        Generator.permutation(ALPHABET.split(""))
                                .withRepetitions(length)
                                .stream()
                )
                .flatMap(Function.identity())
                .skip(startIndex)
                .limit(endIndex - startIndex + 1)
                .map(list -> String.join("", list))
                .toList();
    }

    private void sendResultToManager(String requestId, List<String> words) {
        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitWorkerConfig.CRACK_HASH_EXCHANGE,
                    RabbitWorkerConfig.RESULTS_ROUTING_KEY,
                    response,
                    m -> {
                        m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return m;
                    }
            );
            log.info("Sent results for requestId={}, found {} matches",
                    requestId, words.size());
        } catch (Exception e) {
            log.error("Failed to send results for requestId={}: {}",
                    requestId, e.getMessage());
        }
    }
}