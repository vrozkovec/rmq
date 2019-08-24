package redis.rmq;

public interface Callback {
	public void onMessage(String message);

	default boolean isShutdownRequested() {
		return false;
	}
}
