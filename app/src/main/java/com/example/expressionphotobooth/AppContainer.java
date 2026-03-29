package com.example.expressionphotobooth;

import android.app.Application;

import com.example.expressionphotobooth.data.repository.FirebaseAuthRepository;
import com.example.expressionphotobooth.data.repository.SharedPrefsSessionRepository;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.utils.LocaleManager;

// AppContainer dong vai tro noi giu cac dependency dung chung toan app.
public class AppContainer extends Application {
	private SessionRepository sessionRepository;
	private AuthRepository authRepository;

	@Override
	public void onCreate() {
		super.onCreate();
		LocaleManager.applySavedLocale(this);
		sessionRepository = new SharedPrefsSessionRepository(this);
		authRepository = new FirebaseAuthRepository();
	}

	public SessionRepository getSessionRepository() {
		return sessionRepository;
	}

	public AuthRepository getAuthRepository() {
		return authRepository;
	}
}

