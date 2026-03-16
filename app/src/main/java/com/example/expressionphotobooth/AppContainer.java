package com.example.expressionphotobooth;

import android.app.Application;

import com.example.expressionphotobooth.data.repository.SharedPrefsSessionRepository;
import com.example.expressionphotobooth.domain.repository.SessionRepository;

// AppContainer dong vai tro noi giu cac dependency dung chung toan app.
public class AppContainer extends Application {
	private SessionRepository sessionRepository;

	@Override
	public void onCreate() {
		super.onCreate();
		sessionRepository = new SharedPrefsSessionRepository(this);
	}

	public SessionRepository getSessionRepository() {
		return sessionRepository;
	}
}

