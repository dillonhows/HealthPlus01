package com.example.clienttest.authorization;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionRepository;
import org.springframework.social.connect.DuplicateConnectionException;
import org.springframework.social.greenhouse.api.Greenhouse;
import org.springframework.social.greenhouse.connect.GreenhouseConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.example.clienttest.AbstractGreenhouseActivity;
import com.example.clienttest.MainActivity;
import com.example.clienttest.R;
import com.example.clienttest.getprotectresource.GetProtectActivity;
import com.example.clienttest.profile.ProfileActivity;

public class SignInActivity extends AbstractGreenhouseActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sign_in);
		
		findViewById(R.id.button_signin_cancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View view) {
				finish();
			}
		});
		
		findViewById(R.id.button_signin_signin).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View view) {
				if (validateFormData()) {
					new SignInTask().execute();
				} else {
					displayAppAuthorizationError("Your email or password was entered incorrectly.");
				}
			}
		});
	}
	
	// ***************************************
	// Private methods
	// ***************************************
	private boolean validateFormData() {
		EditText editText = (EditText) findViewById(R.id.signin_email);
		String email = editText.getText().toString().trim();
		editText = (EditText) findViewById(R.id.signin_password);
		String password = editText.getText().toString().trim();
		if (email.length() > 0 && password.length() > 0) {
			return true;
		}
		return false;
	}
	
	private void displayAppAuthorizationError(String message) {
		new AlertDialog.Builder(this).setMessage(message).setCancelable(false)
				.setPositiveButton("OK", null).create().show();
	}

	private void displayGreenhouseOptions() {
		Intent intent = new Intent();
		intent.setClass(this, ProfileActivity.class);
		startActivity(intent);
		setResult(RESULT_OK);
		finish();
	}
	
	private AccessGrant extractAccessGrant(Map<String, Object> result) {
		return new AccessGrant((String) result.get("access_token"), (String) result.get("scope"),
				(String) result.get("refresh_token"), getIntegerValue(result, "expires_in"));
	}

	// Retrieves object from map into an Integer, regardless of the object's actual type. Allows for flexibility in object type (eg, "3600" vs 3600).
	private Integer getIntegerValue(Map<String, Object> map, String key) {
		try {
			return Integer.valueOf(String.valueOf(map.get(key))); // normalize to String before creating integer value;			
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.sign_in, menu);
		return true;
	}

	
	//***************************************
	// Private classes
	//***************************************
	private class SignInTask extends AsyncTask<Void, Void, Void> {
		private MultiValueMap<String, String> formData;

		private Exception exception;

		@Override
		protected void onPreExecute() {
			showProgressDialog("Signing in...");

			EditText editText = (EditText) findViewById(R.id.signin_email);
			String email = editText.getText().toString().trim();

			editText = (EditText) findViewById(R.id.signin_password);
			String password = editText.getText().toString().trim();

			formData = new LinkedMultiValueMap<String, String>();
			formData.add("grant_type", "password");
			formData.add("username", email);
			formData.add("password", password);
			formData.add("client_id", getApplicationContext().getClientId());
			formData.add("client_secret", getApplicationContext().getClientSecret());
			formData.add("scope", "read,write");
			Log.d(TAG, formData.toString());
		}

		@Override
		@SuppressWarnings("unchecked")
		protected Void doInBackground(Void... params) {
			try {
				final String url = getApplicationContext().getApiUrlBase() + "oauth/token";
				Log.d(TAG, url);
				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
				HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<MultiValueMap<String, String>>(formData, requestHeaders);
				RestTemplate restTemplate = new RestTemplate(true);
				Map<String, Object> responseBody = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class).getBody();
				Log.d(TAG, responseBody.toString());

				// Persist the connection and AccessGrant to the repository
				AccessGrant accessGrant = extractAccessGrant(responseBody);
				GreenhouseConnectionFactory connectionFactory = getApplicationContext().getConnectionFactory();
				ConnectionRepository connectionRepository = getApplicationContext().getConnectionRepository();
				Connection<Greenhouse> connection = connectionFactory.createConnection(accessGrant);
				connectionRepository.addConnection(connection);
			} catch (Exception e) {
				Log.e(TAG, e.getLocalizedMessage(), e);
				this.exception = e;
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			dismissProgressDialog();
			if (exception != null) {
				String message;
				if (exception instanceof HttpClientErrorException
						&& ((((HttpClientErrorException) exception).getStatusCode() == HttpStatus.BAD_REQUEST)
						|| ((HttpClientErrorException) exception).getStatusCode() == HttpStatus.UNAUTHORIZED)) {
					message = "Auth failure,Your email or password was entered incorrectly.";
				} else if (exception instanceof DuplicateConnectionException) {
					message = "The connection already exists.";
				} else {
					Log.e(TAG, exception.getLocalizedMessage(), exception);
					message = "A problem occurred with the network connection. Please try again in a few minutes.";
				}
				displayAppAuthorizationError(message);
			} else {
				displayGreenhouseOptions();
			}
		}
	}
}
