package pArtapibeta;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

import pArtapibeta.test.pArtapibeta.TestUser;
import test.api.picsart.com.picsart_api_test.PicsArtConst;


public class MainActivity extends Activity implements RequestListener {

    public static final String MY_LOGS = "My_Logs";

    private static Context context;
    private WebView web;
    private Button auth;
    private SharedPreferences pref;
    private TextView Access;
    private ProgressDialog pDialog;
    private Button testcallBtt;
    private static String token;


    public static Context getAppContext() {
        return MainActivity.context;
    }

    public static String getAccessToken() {
        return token;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        testcallBtt = (Button) findViewById(R.id.testCall);

        Access = (TextView) findViewById(R.id.Access);
        auth = (Button) findViewById(R.id.auth);
        MainActivity.context = getApplicationContext();
        try {
            pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            token = pref.getString("access_Token", "");
            //Toast.makeText(getApplicationContext(), "Access Token " + token, Toast.LENGTH_LONG).show();
        } catch (Exception c) {
            Log.i("ERROR Loading Token ", ": no token is persist ");
        }

        auth.setOnClickListener(new View.OnClickListener() {
            Dialog authDialog;

            @Override
            public void onClick(View view) {
                authDialog = new Dialog(MainActivity.this);
                authDialog.setContentView(R.layout.auth_dialog);
                web = (WebView) authDialog.findViewById(R.id.webv);
                web.getSettings().setJavaScriptEnabled(true);
                web.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                web.getSettings().setSupportMultipleWindows(true);
                //web.setWebViewClient(new SomWebViewDefClient());
                web.setWebViewClient(new WebViewClient() {
                    boolean authComplete = false;
                    Intent resultIntent = new Intent();

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        Log.i("ShouldOvver", " rideUrlLoading" + url);
                        return false;
                    }

                    @Override
                    public void onPageStarted(WebView view, String url,
                                              Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        // view.setWebChromeClient(new MyWebChromeClient());
                        authComplete = false;
                        pDialog = ProgressDialog.show(view.getContext(), "",
                                "Connecting to " + " server", false);
                        pDialog.dismiss();

                    }

                    String authCode;

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        pDialog.dismiss();

                        Log.i("COOODEE", "LINE 82 : " + authCode);
                        if (url.contains("?code=") && authComplete != true) {

                            Uri uri = Uri.parse(url);
                            authCode = uri.getQueryParameter("code");
                            Log.i("COOODEE", "CODE : " + authCode);
                            authComplete = true;
                            resultIntent.putExtra("code", authCode);
                            MainActivity.this.setResult(Activity.RESULT_OK,
                                    resultIntent);
                            setResult(Activity.RESULT_CANCELED, resultIntent);

                            SharedPreferences.Editor edit = pref.edit();
                            edit.putString("Code", authCode);
                            edit.commit();

                            new TokenGet().execute();

                            Toast.makeText(getApplicationContext(),
                                    "Authorization Code is: " + authCode,
                                    Toast.LENGTH_LONG).show();
                            authDialog.dismiss();
                        } else if (url.contains("error=access_denied")) {
                            Log.i("", "ACCESS_DENIED_HERE");
                            resultIntent.putExtra("code", authCode);
                            authComplete = true;
                            setResult(Activity.RESULT_CANCELED, resultIntent);
                            Toast.makeText(getApplicationContext(),
                                    "Error Occured", Toast.LENGTH_SHORT).show();

                        }
                    }
                });

                String authURL = PicsArtConst.OAUTH_URL + "?redirect_uri=" + PicsArtConst.REDIRECT_URI + "&response_type=code&client_id=" + PicsArtConst.CLIENT_ID;
                String tokenURL = PicsArtConst.TOKEN_URL + "?client_id=" + PicsArtConst.CLIENT_ID + "&client_secret=" + PicsArtConst.CLIENT_SECRET + "&redirect_uri=" + PicsArtConst.REDIRECT_URI + "&grant_type=authorization_code";

                web.loadUrl(authURL);
                // web.loadUrl("http://stage.i.picsart.com/api/oauth2/authorize?redirect_uri=localhost&response_type=code&client_id=armantestclient1nHhXPI9ZqwQA03XI");
                authDialog.show();
                authDialog.setTitle("Authorize PicsArt");
                authDialog.setCancelable(true);
            }
        });
    }

    private class TokenGet extends AsyncTask<String, String, JSONObject> {
        private ProgressDialog pDialog;
        String Code;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage("Contacting ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            Code = pref.getString("Code", "");
            pDialog.show();
        }

        @Override
        protected JSONObject doInBackground(String... args) {
            GetAccessToken jParser = new GetAccessToken(MainActivity.context);
            JSONObject json = jParser.gettoken(PicsArtConst.TOKEN_URL, Code, PicsArtConst.CLIENT_ID,
                    PicsArtConst.CLIENT_SECRET, PicsArtConst.REDIRECT_URI, PicsArtConst.GRANT_TYPE);
            return json;
        }

        @Override
        protected void onPostExecute(JSONObject json) {

            if (json != null) {

                try {

                    String tok = json.getString("access_token");
                    // String expire = json.getString("expires_in");
                    // String refresh = json.getString("refresh_token");
                    Log.d("Token Access", tok);
                    SharedPreferences prrefs = PreferenceManager.getDefaultSharedPreferences(
                            MainActivity.this);
                    prrefs.edit().putString("access_Token", tok).commit();

                    pDialog.dismiss();
                    Toast.makeText(getApplicationContext(), "Access Token: " + tok, Toast.LENGTH_LONG).show();
                    token = tok;
                    auth.setText("Authenticated");

                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            } else {
                Toast.makeText(getApplicationContext(), "Network Error",
                        Toast.LENGTH_SHORT).show();
                pDialog.dismiss();
            }
        }
    }


    @Override
    public void onRequestReady(int reqnumber, String msg) {

        if (reqnumber == 1) {
            Log.d("MainListener", "reqnumber= " + reqnumber);
        }
    }

    public void onTestCallClick(View v) {

        final UserController userController = new UserController(getApplicationContext());
        //Photo photo = new Photo(Photo.IS.GENERAL);

        //photo.setPath("/storage/sdcard0/aa.jpg");

        //userController.requestUserFollowing("me", 0, UserController.MAX_LIMIT);  //  9
        //userController.requestUserFollowers("me",0,UserController.MAX_LIMIT);  //  8
        //userController.requestLikedPhotos("me",0,UserController.MAX_LIMIT);    //  10
        //userController.requestBlockedUsers("me",0,UserController.MAX_LIMIT);   //  4
        //userController.requestTags("me",2,6);                                  //  6
        //userController.requestUserPhotos("me",0,UserController.MAX_LIMIT);     //  7

        //userController.followUserWithID("45033295");
        //userController.unblockUserWithID("153741055000102","160573178000102");
        //userController.blockUserWithID("153741055000102");
        //userController.requestUser("156064667000102");   //  5
        //userController.requestUser();

        //userController.uploadUserPhoto(photo);

        /*userController.setListener(new RequestListener() {
            @Override
            public void onRequestReady(int requmber, String msg) {
                if (requmber == 102) {

                    Log.d(MY_LOGS,""+userController.getUser().getTagsCount());
                    Log.d(MY_LOGS,""+userController.getUser().getEmail());
                    Log.d(MY_LOGS,""+userController.getUser().getCover());
                    Log.d(MY_LOGS,""+userController.getUser().getTags().size());

                }
                if (requmber == 205) {

                    Log.d(MY_LOGS, userController.getUser().getName());

                }
            }
        });*/

        userController.setSt_listener(new RequestListener() {
            @Override
            public void onRequestReady(int requmber, String message) {

                if (requmber == 118) {
                    Log.d(MY_LOGS, "userProfile id :  " + message);
                }
                if (requmber == 116) {
                    Log.d(MY_LOGS, "userProfile:  " + message);
                }
            }
        });

        //TestUser.testUserFollowing("me");
        //TestUser.testUserLikedPhotos("me");
        //TestUser.testUserBlockedUsers("me");
        //TestUser.testUserPlaces("me");
        //TestUser.testUserProfile();
        //TestUser.testUserProfile("me");
        TestUser.testUserTags("me");
    }
}