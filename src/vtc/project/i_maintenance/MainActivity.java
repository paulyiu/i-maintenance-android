package vtc.project.i_maintenance;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.plus.People;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.People.LoadPeopleResult;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.plus.model.people.PersonBuffer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity implements ConnectionCallbacks,
		OnConnectionFailedListener, ResultCallback<People.LoadPeopleResult>,
		View.OnClickListener {

	private static final String TAG = "android-plus-quickstart";
	public static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	private int mSignInProgress;
	private static final int STATE_DEFAULT = 0;
	private static final int STATE_SIGN_IN = 1;
	private static final int STATE_IN_PROGRESS = 2;

	private static final int RC_SIGN_IN = 0;

	private static final int DIALOG_PLAY_SERVICES_ERROR = 0;
	private static final String SAVED_PROGRESS = "sign_in_progress";

	private GoogleApiClient mGoogleApiClient;
	private PendingIntent mSignInIntent;
	private int mSignInError;

	private SignInButton mSignInButton;
	private ImageView mIcon;
	private Button mSignOutButton;
	private Button mRevokeButton;
	private TextView mStatus;
	private ListView mCirclesListView;
	private ArrayAdapter<String> mCirclesAdapter;
	private ArrayList<String> mCirclesList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
		mSignOutButton = (Button) findViewById(R.id.sign_out_button);
		mRevokeButton = (Button) findViewById(R.id.revoke_access_button);
		mStatus = (TextView) findViewById(R.id.sign_in_status);
		//mCirclesListView = (ListView) findViewById(R.id.circles_list);
		mIcon = (ImageView) findViewById(R.id.imageView1);

		mSignInButton.setOnClickListener(this);
		mSignOutButton.setOnClickListener(this);
		mRevokeButton.setOnClickListener(this);

		mCirclesList = new ArrayList<String>();
		mCirclesAdapter = new ArrayAdapter<String>(this,
				R.layout.circle_member, mCirclesList);
		//mCirclesListView.setAdapter(mCirclesAdapter);

		if (savedInstanceState != null) {
			mSignInProgress = savedInstanceState.getInt(SAVED_PROGRESS,
					STATE_DEFAULT);
		}

		mGoogleApiClient = buildGoogleApiClient();
	}

	private GoogleApiClient buildGoogleApiClient() {
		return new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(Plus.API, Plus.PlusOptions.builder().build())
				.addScope(Plus.SCOPE_PLUS_LOGIN).build();
	}

	@Override
	protected void onStart() {
		super.onStart();

		mGoogleApiClient.connect();
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (mGoogleApiClient.isConnected()) {
			mGoogleApiClient.disconnect();
		}
	}

	protected void onSaveIntanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(SAVED_PROGRESS, mSignInProgress);
	}

	@Override
	public void onClick(View v) {
		if (!mGoogleApiClient.isConnecting()) {
			switch (v.getId()) {
			case R.id.sign_in_button:
				mStatus.setText(R.string.status_signing_in);
				resolveSignInError();
				break;
			case R.id.sign_out_button:
				Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
				mGoogleApiClient.disconnect();
				mGoogleApiClient.connect();
				break;
			case R.id.revoke_access_button:
				Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
				Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient);
				mGoogleApiClient = buildGoogleApiClient();
				mGoogleApiClient.connect();
				break;
			}
		}

	}

	@Override
	public void onConnected(Bundle connectionHint) {
		mSignInButton.setEnabled(false);
		mSignOutButton.setEnabled(true);
		mRevokeButton.setEnabled(true);
		Log.i(TAG, "onConnected:");
		Person currentUser = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
		mStatus.setText(String.format(
				getResources().getString(R.string.signed_in_as),
						currentUser.getDisplayName()) );

		new LoadProfileImage(mIcon).execute(currentUser.getImage().getUrl() + "&sz=150");
		Plus.PeopleApi.loadVisible(mGoogleApiClient, null).setResultCallback(
				this);

		mSignInProgress = STATE_DEFAULT;
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		Log.i(TAG, "onConnectionFailed: ConnectionResult.getErrorCode() = "
				+ result.getErrorCode());

		if (mSignInProgress != STATE_IN_PROGRESS) {
			mSignInIntent = result.getResolution();
			mSignInError = result.getErrorCode();
		}

		if (mSignInProgress == STATE_SIGN_IN) {
			resolveSignInError();
		}

		onSignedOut();

	}

	private void resolveSignInError() {
		if (mSignInIntent != null) {
			try {
				mSignInProgress = STATE_IN_PROGRESS;
				startIntentSenderForResult(mSignInIntent.getIntentSender(),
						RC_SIGN_IN, null, 0, 0, 0);
				Log.i(TAG, "resolvSignInError():");
			} catch (SendIntentException e) {
				Log.i(TAG,
						"Sign in intent could not to send:"
								+ e.getLocalizedMessage());
				mSignInProgress = STATE_SIGN_IN;
				mGoogleApiClient.connect();
			}
		} else {
			showDialog(DIALOG_PLAY_SERVICES_ERROR);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case RC_SIGN_IN:
			if (resultCode == RESULT_OK) {
				Log.i(TAG, "resolvSignInError():resultCode == RESULT_OK");
				mSignInProgress = STATE_SIGN_IN;
			} else {
				mSignInProgress = STATE_DEFAULT;
			}

			if (!mGoogleApiClient.isConnected()) {
				mGoogleApiClient.connect();
			}
			break;
		}
	}

	@Override
	public void onResult(LoadPeopleResult peopleData) {
		if (peopleData.getStatus().getStatusCode() == CommonStatusCodes.SUCCESS) {
			mCirclesList.clear();
			PersonBuffer personBuffer = peopleData.getPersonBuffer();
			try {
				int count = personBuffer.getCount();
				for (int i = 0; i < count; i++) {
					mCirclesList.add(personBuffer.get(i).getDisplayName());
				}
			} finally {
				personBuffer.close();
			}

			mCirclesAdapter.notifyDataSetChanged();
		} else {
			Log.e(TAG,
					"Error requesting visible circles: "
							+ peopleData.getStatus());
		}

	}

	private void onSignedOut() {
		mSignInButton.setEnabled(true);
		mSignOutButton.setEnabled(false);
		mRevokeButton.setEnabled(false);
		mIcon.setImageResource(R.drawable.common_signin_btn_icon_dark);
		mStatus.setText(R.string.status_signed_out);

		mCirclesList.clear();
		mCirclesAdapter.notifyDataSetChanged();
	}

	@Override
	public void onConnectionSuspended(int cause) {
		mGoogleApiClient.connect();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_PLAY_SERVICES_ERROR:
			if (GooglePlayServicesUtil.isUserRecoverableError(mSignInError)) {
				return GooglePlayServicesUtil.getErrorDialog(mSignInError,
						this, RC_SIGN_IN,
						new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface dialog) {
								Log.e(TAG,
										"Google Play services resolution cancelled");
								mSignInProgress = STATE_DEFAULT;
								mStatus.setText(R.string.status_signed_out);
							}
						});
			} else {
				return new AlertDialog.Builder(this)
						.setMessage(R.string.play_services_error)
						.setPositiveButton(R.string.close,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										Log.e(TAG,
												"Google Play services error could not be resolved: "
														+ mSignInError);
									}
								}).create();
			}
		default:
			return super.onCreateDialog(id);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}

/**
 * Background Async task to load user profile picture from url
 * */
class LoadProfileImage extends AsyncTask<String, Void, Bitmap> {
	ImageView bmImage;

	public LoadProfileImage(ImageView bmImage) {
		this.bmImage = bmImage;
	}

	protected Bitmap doInBackground(String... urls) {
		String urldisplay = urls[0];
		Bitmap mIcon11 = null;
		try {
			InputStream in = new java.net.URL(urldisplay).openStream();
			mIcon11 = BitmapFactory.decodeStream(in);
		} catch (Exception e) {
			Log.e("Error", e.getMessage());
			e.printStackTrace();
		}
		return mIcon11;
	}

	protected void onPostExecute(Bitmap result) {
		bmImage.setImageBitmap(result);
	}
}
