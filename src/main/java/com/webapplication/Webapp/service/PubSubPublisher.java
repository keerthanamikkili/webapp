package com.webapplication.Webapp.service;

import com.webapplication.Webapp.entity.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PubSubPublisher {

    private final String projectId;
    private final String topicId;

    public PubSubPublisher(@Value("${pubsub.projectId}") String projectId,
            @Value("${pubsub.topicId}") String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public void publishUserInformation(User user,String verificationLink) {
        Publisher publisher = null;
        try {
            TopicName topicName = TopicName.of(projectId, topicId);
            publisher = Publisher.newBuilder(topicName).build();

            // String jsonPayload = String.format("{\"UserName\":\"%s\",\"UserId\":\"%s\"}",
            // user.getUsername(), user.getId().toString());

            // PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
            // .setData(ByteString.copyFromUtf8(jsonPayload))
            // .build();

            // Concatenate user data into a JSON string
            String jsonPayload = "{\"username\": \"" + user.getUsername() + "\", \"verificationLink\": \""
                    + verificationLink + "\"}";

            // Create a PubsubMessage with the JSON payload
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(jsonPayload))
                    .build();

            ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
            String messageId = messageIdFuture.get();
            System.out.println("Published message ID: " + messageId);
        } catch (Exception e) {

            e.printStackTrace();
        } finally {
            if (publisher != null) {
                try {
                    publisher.shutdown();
                    publisher.awaitTermination(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
