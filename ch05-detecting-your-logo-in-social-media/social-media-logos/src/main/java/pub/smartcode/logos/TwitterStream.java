package pub.smartcode.logos;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.event.Event;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TwitterStream implements Runnable {
    private BlockingQueue<String> msgQueue;
    private BlockingQueue<Map<String,Object>> imageQueue;
    private Client client;
    private Gson gson;

    public TwitterStream(Gson gson, Properties props, BlockingQueue<Map<String,Object>> imageQueue) {
        this.gson = gson;
        this.imageQueue = imageQueue;

        msgQueue = new LinkedBlockingQueue<String>(100000);

        Hosts hosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint endpoint = new StatusesFilterEndpoint();

        List<String> terms = Lists.newArrayList(
                props.getProperty("twitter_terms")
                        .split("\\s*,\\s*"));
        endpoint.trackTerms(terms);

        Authentication auth = new OAuth1(
                props.getProperty("twitter_consumer_key"),
                props.getProperty("twitter_consumer_secret"),
                props.getProperty("twitter_token"),
                props.getProperty("twitter_token_secret"));
        ClientBuilder builder = new ClientBuilder()
                .name("SmartCode-Client-01")
                .hosts(hosts)
                .authentication(auth)
                .endpoint(endpoint)
                .processor(new StringDelimitedProcessor(msgQueue));
        client = builder.build();
        client.connect();
    }

    public void run() {
        try {
            while (!client.isDone()) {
                String msg = msgQueue.take();
                Map<String, Object> msgobj = gson.fromJson(msg, Map.class);
                Map<String, Object> entities = (Map<String, Object>)msgobj.get("entities");
                List<Map<String, Object>> media = (List<Map<String, Object>>)entities.get("media");
                if(media != null) {
                    for(Map<String, Object> entity : media) {
                        String type = (String)entity.get("type");
                        if(type.equals("photo")) {
                            imageQueue.add(msgobj);
                        }
                    }
                }
            }
        } catch(InterruptedException e) {
            client.stop();
        }
    }
}
