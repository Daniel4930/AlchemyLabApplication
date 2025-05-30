package com.alchemyLab.general_chem_website.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.alchemyLab.general_chem_website.util.ResponseUtil;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.Map;

@Service
public class EmailService {
    @Value("${aws.ses.senderEmail}")
    private String sender;

    @Value("${aws.service.region}")
    private String awsRegion;

    private Region region;

    @PostConstruct
    public void init() {
        region = Region.of(awsRegion);
    }

    @PostConstruct
    public void logSender() {
        System.out.println("Effective sender email: " + sender);
    }

    private SesClient buildSesClient() {
        return SesClient.builder().region(region).build();
    }

    private SendEmailRequest setUpEmail(String email, String link) {
        String bodyHTML =String.format(
            """
            <h1>Password Reset</h1>
            <h3>Link will expire after 30 minutes</h3>
            <p>
                <a href="%s">Click here</a>
            </p>
            """
        , link);

        Destination destination = Destination.builder().toAddresses(email).build();
        Content content = Content.builder().data(bodyHTML).build();
        Content subject = Content.builder().data("Reset password link from AlchemyLab").build();
        Body body = Body.builder().html(content).build();
        Message message = Message.builder().subject(subject).body(body).build();

        System.out.println("Effective sender email: " + sender);
        return SendEmailRequest.builder().destination(destination).message(message).source(sender).build();
    }
    
    public ResponseEntity<Map<String, Object>> sendEmail(String email, String link) throws SesException {
        SendEmailRequest emailRequest = setUpEmail(email, link);

        try (SesClient client = buildSesClient()) {
            client.sendEmail(emailRequest);
            return ResponseEntity.ok(ResponseUtil.createResponse("Reset email sent"));
        } catch (SesException error) {
            System.err.printf("SesException error (%s): %s", error.getClass().getName(), error.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseUtil.createResponse("Server error: Can't send email"));
        }
    }
}
