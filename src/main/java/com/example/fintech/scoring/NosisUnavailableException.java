package com.example.fintech.scoring;

public class NosisUnavailableException extends RuntimeException {

	public NosisUnavailableException(String message) {
		super(message);
	}

	public NosisUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
