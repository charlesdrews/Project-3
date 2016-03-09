package com.charlesdrews.hud;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;

import com.charlesdrews.hud.CardsData.CardData;
import com.charlesdrews.hud.CardsData.CardType;
import com.charlesdrews.hud.CardsData.FacebookCardData;
import com.charlesdrews.hud.CardsData.NewsCardData;
import com.charlesdrews.hud.CardsData.WeatherCardData;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ArrayList<CardData> mCardsData;
    private RecyclerView.Adapter mAdapter;
    private Account mAccount;
    public LoginButton mFacebookLoginButton;
    public CallbackManager mCallbackManager;
    private TextView mLoginText;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //TODO facebook stuff
        mLoginText = (TextView)findViewById(R.id.status_update);
        //TODO - can this initialization be done in an async task?
        FacebookSdk.sdkInitialize(getApplicationContext());

        // register content observers
        getContentResolver().registerContentObserver(
                Uri.parse(CardContentProvider.BASE_URI_STRING), // base uri w/o API-specific path
                true,                                           // notify for API-specific paths
                new CardContentObserver(new Handler())
        );

        // set up recycler view & adapter
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        RecyclerView.LayoutManager manager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(manager);
        mCardsData = new ArrayList<>();
        mAdapter = new RecyclerAdapter(mCardsData);
        recyclerView.setAdapter(mAdapter);

        // set up syncing
        mAccount = createSyncAccount(this);
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(mAccount, CardContentProvider.AUTHORITY, settingsBundle);
        ContentResolver.setSyncAutomatically(mAccount, CardContentProvider.AUTHORITY, true);
        //TODO - figure out why this is syncing so frequently (definitely not just every 15 min)
        ContentResolver.addPeriodicSync(mAccount, CardContentProvider.AUTHORITY, Bundle.EMPTY, 60 * 15);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                //TODO - launch settings activity
                return true;
            /*
            case R.id.search:
                //TODO - setup and launch search
                return true;
                */
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public class CardContentObserver extends ContentObserver {
        private final String TAG = CardContentObserver.class.getCanonicalName();

        public CardContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            int uriType = CardContentProvider.sUriMatcher.match(uri);

            switch (uriType) {
                case CardContentProvider.FACEBOOK: {
                    Log.d(TAG, "onChange: facebook");
                    new UpdateDataAsyncTask().execute(CardType.Facebook);
                    break;
                }
                case CardContentProvider.NEWS: {
                    Log.d(TAG, "onChange: news");
                    new UpdateDataAsyncTask().execute(CardType.News);
                    break;
                }
                case CardContentProvider.WEATHER: {
                    Log.d(TAG, "onChange: weather");
                    new UpdateDataAsyncTask().execute(CardType.Weather);
                    break;
                }
                default:
                    break;
            }
        }
    }

    public static Account createSyncAccount(Context context) {
        Account newAccount = new Account(
                context.getString(R.string.account),
                context.getString(R.string.account_type)
        );

        AccountManager accountManager =
                (AccountManager) context.getSystemService(ACCOUNT_SERVICE);

        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            Log.d(MainActivity.class.getCanonicalName(), "Account added successfully");
        } else {
            Log.d(MainActivity.class.getCanonicalName(), "Account NOT added successfully");
        }
        return newAccount;
    }

    private class UpdateDataAsyncTask extends AsyncTask<CardType, Void, Boolean> {
        private final String TAG = UpdateDataAsyncTask.class.getCanonicalName();

        private CardType mCardType;

        @Override
        protected Boolean doInBackground(CardType... params) {
            if ((mCardType = params[0]) == null) {
                cancel(true);
            }

            switch (mCardType) {
                case Facebook: {
                    Log.d(TAG, "doInBackground: query facebook");
                    return updateCardDataArrayFromCursor(getContentResolver().query(
                            CardContentProvider.FACEBOOK_URI, null, null, null, null));
                }
                case News: {
                    Log.d(TAG, "doInBackground: query news");
                    return updateCardDataArrayFromCursor(getContentResolver().query(
                            CardContentProvider.NEWS_URI, null, null, null, null));
                }
                case Weather: {
                    Log.d(TAG, "doInBackground: query weather");
                    return updateCardDataArrayFromCursor(getContentResolver().query(
                            CardContentProvider.WEATHER_URI, null, null, null, null));
                }
                default:
                    return null;
            }
        }

        private boolean updateCardDataArrayFromCursor(Cursor cursor) {
            boolean dataAddedSuccessfully = false;

            if (cursor != null && cursor.moveToFirst()) {
                // remove any pre-existing card data objects of same type
                for (int i = 0; i < mCardsData.size(); i++) {
                    if (mCardsData.get(i).getType() == mCardType) {
                        mCardsData.remove(i);
                    }
                }

                // insert new card data object
                switch (mCardType) {
                    case Facebook: {
                        FacebookCardData facebookCardData = new FacebookCardData(
                                CardType.Facebook,
                                cursor.getString(cursor.getColumnIndex(DatabaseHelper.FACEBOOK_COL_AUTHOR)),
                                cursor.getString(cursor.getColumnIndex(DatabaseHelper.FACEBOOK_COL_STATUS_UPDATE))
                        );
                        mCardsData.add(0, facebookCardData);
                        dataAddedSuccessfully = true;
                        break;
                    }
                    case News: {
                        NewsCardData newsCardData = new NewsCardData(
                                CardType.News,
                                cursor.getString(cursor.getColumnIndex(DatabaseHelper.NEWS_COL_HEADLINE))
                        );
                        mCardsData.add(0, newsCardData);
                        dataAddedSuccessfully = true;
                        break;
                    }
                    case Weather: {
                        WeatherCardData weatherCardData = new WeatherCardData(
                                CardType.Weather,
                                cursor.getInt(cursor.getColumnIndex(DatabaseHelper.WEATHER_COL_HIGH)),
                                cursor.getInt(cursor.getColumnIndex(DatabaseHelper.WEATHER_COL_LOW))
                        );
                        mCardsData.add(0, weatherCardData);
                        dataAddedSuccessfully = true;
                        break;
                    }
                    default:
                        dataAddedSuccessfully = false;
                        break;
                }
                cursor.close();
            }
            return dataAddedSuccessfully;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            if (aBoolean) {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    public void facebookLogin(){

        mFacebookLoginButton = (LoginButton)findViewById(R.id.login_button);
        mFacebookLoginButton.setReadPermissions("user_likes");
        mCallbackManager = CallbackManager.Factory.create();
        mFacebookLoginButton.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Toast.makeText(MainActivity.this, "Logged in successfully", Toast.LENGTH_SHORT).show();
                mLoginText.setText("User ID: " + loginResult.getAccessToken().getUserId() + "Auth token: " + loginResult.getAccessToken().getToken());

            }

            @Override
            public void onCancel() {
                Toast.makeText(MainActivity.this, "Login canceled", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(FacebookException error) {
                Toast.makeText(MainActivity.this, "Login error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //TODO - this crashes if you close the login window without logging in
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }
}
