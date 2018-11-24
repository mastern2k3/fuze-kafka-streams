package com.tikal.kafkafuze;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launch {

    private static Logger logger = LoggerFactory.getLogger(Launch.class);

    public static void main(String[] args) {

// logger
        // System.setProperty(org.slf4j.impl., "TRACE");

        System.out.print("Hi what's up?");

        String consumerKey = "e8kDdf6ac97M4KqlaSoCKKBf4";
        String consumerSecret = "sGLu4NAOHNZjGt7NCf21Iz8xGtB4ejkS0IQWmWblq5uKLRGoWy";
        String token = "125274693-9Nsp6DzgBE5zniFE8vOaWoJoR2XnhqSF5qETawsI";
        String secret = "mkuu4Lde4T2pwwOSiHovdsvaCiEe0mVJAjG5BksqjhInA";

        try {
            run(consumerKey, consumerSecret, token, secret);

        } catch (InterruptedException e) {
            logger.error("fatal error while consuming tweets", e);
            // e.printStackTrace();
        }
    }

    public static void run(String consumerKey, String consumerSecret, String token, String secret)
            throws InterruptedException {

        BlockingQueue<String> queue = new LinkedBlockingQueue<String>(10000);
        StatusesFilterEndpoint endpoint = new StatusesFilterEndpoint();

        // add some track terms
        endpoint.trackTerms(Lists.newArrayList("color"));

        Authentication auth = new OAuth1(consumerKey, consumerSecret, token, secret);
        // Authentication auth = new BasicAuth(username, password);

        // Create a new BasicClient. By default gzip is enabled.
        Client client =
            new ClientBuilder()
                .hosts(Constants.STREAM_HOST)
                .endpoint(endpoint)
                .authentication(auth)
                .processor(new StringDelimitedProcessor(queue))
                .build();

        // Establish a connection
        client.connect();

        try {
            // Do whatever needs to be done with messages
            while (true) {
                String msg = queue.take();
                logger.info("message arrived: {}", msg);
            }
        } finally {
            client.stop();
        }
    }
}
