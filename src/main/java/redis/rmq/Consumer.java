package redis.rmq;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Tuple;

public class Consumer {

	private static final Logger log = LoggerFactory.getLogger(Consumer.class);

	private Nest topic;
	private Nest subscriber;
	private String id;

	private String topicName;

	public Consumer(final JedisPool jedisPool, final String id, final String topic) {
		this.topic = new Nest("topic:" + topic, jedisPool);
		this.subscriber = new Nest(this.topic.cat("subscribers").key(), jedisPool);
		this.id = id;
		topicName = topic;
	}

	private void waitForMessages() {
		try {
			// TODO el otro metodo podria hacer q no se consuman mensajes por un
			// tiempo si no llegan, de esta manera solo se esperan 500ms y se
			// controla que haya mensajes.
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}
	}

	public void consume(Callback callback) {
		while (true) {
			// allow for safe exit
			if (callback.isShutdownRequested()) {
				log.info("Shutdown requested. Stopping queue {}", topicName);
				break;
			}
			String message = readUntilEnd();
			if (message != null) {
				callback.onMessage(message);
				log.info("Remaining messages in queue {}: {}", topicName, unreadMessages());
			} else
				waitForMessages();
		}
	}

	public String consume() {
		return readUntilEnd();
	}

	private String readUntilEnd() {
		while (unreadMessages() > 0) {
			String message = read();
			goNext();
			if (message != null)
				return message;
		}

		return null;
	}

	private void goNext() {
		subscriber.zincrby(1, id);
	}

	private int getLastReadMessage() {
		Double lastMessageRead = subscriber.zscore(id);
		if (lastMessageRead == null) {
			Set<Tuple> zrangeWithScores = subscriber.zrangeWithScores(0, 1);
			if (zrangeWithScores.iterator().hasNext()) {
				Tuple next = zrangeWithScores.iterator().next();
				Integer lowest = (int) next.getScore() - 1;
				subscriber.zadd(lowest, id);
				return lowest;
			} else {
				return 0;
			}
		}
		return lastMessageRead.intValue();
	}

	private int getTopicSize() {
		String stopicSize = topic.get();
		int topicSize = 0;
		if (stopicSize != null) {
			topicSize = Integer.valueOf(stopicSize);
		}
		return topicSize;
	}

	public String read() {
		int lastReadMessage = getLastReadMessage();
		return topic.cat("message").cat(lastReadMessage + 1).get();
	}

	public int unreadMessages() {
		return getTopicSize() - getLastReadMessage();
	}
}