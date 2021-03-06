package openfoodfacts.github.scrachx.openfood.views;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.IOException;
import java.net.HttpCookie;

import butterknife.BindView;
import butterknife.OnClick;
import okhttp3.ResponseBody;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIService;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.customtabs.CustomTabActivityHelper;
import openfoodfacts.github.scrachx.openfood.views.customtabs.WebViewFallback;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * A login screen that offers login via login/password.
 * This Activity connect to the Chrome Custom Tabs Service on startup to prefetch the url.
 */
public class LoginActivity extends BaseActivity implements CustomTabActivityHelper.ConnectionCallback {

    @BindView(R.id.editTextLogin) EditText loginView;
    @BindView(R.id.editTextPass) EditText passwordView;
    @BindView(R.id.textInfoLogin) TextView infoLogin;
    @BindView(R.id.buttonSave) Button save;
    @BindView(R.id.buttonCreateAccount) Button signup;

    private OpenFoodAPIService apiClient;
    private CustomTabActivityHelper customTabActivityHelper;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        uri = Uri.parse(getString(R.string.website) + "cgi/user.pl");

        // prefetch the uri
        customTabActivityHelper = new CustomTabActivityHelper();
        customTabActivityHelper.setConnectionCallback(this);
        customTabActivityHelper.mayLaunchUrl(uri, null, null);

        signup.setEnabled(false);

        final SharedPreferences settings = getSharedPreferences("login", 0);
        String loginS = settings.getString("user", getResources().getString(R.string.txt_anonymous));
        if(!loginS.equals(getResources().getString(R.string.txt_anonymous))) {
            new MaterialDialog.Builder(this)
                    .title("Login")
                    .content("Already logged in!")
                    .neutralText(R.string.ok_button)
                    .show();
        }

        apiClient = new Retrofit.Builder()
                .baseUrl(this.getString(R.string.openfoodUrl))
                .build()
                .create(OpenFoodAPIService.class);
    }

    @OnClick(R.id.buttonSave)
    protected void attemptLogin() {
        String login = loginView.getText().toString();
        String password = passwordView.getText().toString();

        if (!(password.length() >= 6)) {
            passwordView.setError(getString(R.string.error_invalid_password));
            passwordView.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(login)) {
            loginView.setError(getString(R.string.error_field_required));
            loginView.requestFocus();
            return;
        }

        final LoadToast lt = new LoadToast(this);
        save.setClickable(false);
        lt.setText(getString(R.string.toast_retrieving));
        lt.setBackgroundColor(getResources().getColor(R.color.indigo_600));
        lt.setTextColor(getResources().getColor(R.color.white));
        lt.show();

        final Activity context = this;
        apiClient.signIn(login, password, "Sign-in").enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccess()) {
                    Toast.makeText(context, context.getString(R.string.errorWeb), Toast.LENGTH_LONG).show();
                    lt.error();
                    Utils.hideKeyboard(context);
                    return;
                }

                String htmlNoParsed = null;
                try {
                    htmlNoParsed = response.body().string();
                } catch (IOException e) {
                    Log.e("LOGIN", "Unable to parse the login response page", e);
                }

                SharedPreferences.Editor editor = context.getSharedPreferences("login", 0).edit();

                if (htmlNoParsed == null || htmlNoParsed.contains("Incorrect user name or password.") || htmlNoParsed.contains("See you soon!")) {
                    lt.error();
                    Toast.makeText(context, context.getString(R.string.errorLogin), Toast.LENGTH_LONG).show();
                    loginView.setText("");
                    passwordView.setText("");
                    editor.putString("user", "");
                    editor.putString("pass", "");
                    editor.apply();
                    infoLogin.setText(R.string.txtInfoLoginNo);
                } else {
                    // store the user session id (user_session and user_id)
                    for (HttpCookie httpCookie : HttpCookie.parse(response.headers().get("set-cookie"))) {
                        if (httpCookie.getDomain().equals(".openfoodfacts.org") && httpCookie.getPath().equals("/")) {
                            String[] cookieValues = httpCookie.getValue().split("&");
                            for (int i = 0; i < cookieValues.length; i++) {
                                editor.putString(cookieValues[i], cookieValues[++i]);
                            }

                            break;
                        }
                    }
                    lt.success();
                    Toast.makeText(context, context.getResources().getText(R.string.txtToastSaved), Toast.LENGTH_LONG).show();
                    editor.putString("user", loginView.getText().toString());
                    editor.putString("pass", passwordView.getText().toString());
                    editor.apply();
                    infoLogin.setText(R.string.txtInfoLoginOk);

                    setResult(RESULT_OK, new Intent());
                    finish();
                }
                Utils.hideKeyboard(context);

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(context, context.getString(R.string.errorWeb), Toast.LENGTH_LONG).show();
                lt.error();
                Utils.hideKeyboard(context);
            }
        });

        save.setClickable(true);
    }

    @OnClick(R.id.buttonCreateAccount)
    protected void onCreateUser() {
        Bitmap icon = ((BitmapDrawable) getResources().getDrawable(R.drawable.ic_navigation_arrow_back)).getBitmap();

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder(customTabActivityHelper.getSession())
                .setShowTitle(true)
                .setToolbarColor(getResources().getColor(R.color.indigo_400))
                .setCloseButtonIcon(icon)
                .build();

        CustomTabActivityHelper.openCustomTab(this, customTabsIntent, uri, new WebViewFallback());
    }

    @Override
    public void onCustomTabsConnected() {
        signup.setEnabled(true);
    }

    @Override
    public void onCustomTabsDisconnected() {
        signup.setEnabled(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        customTabActivityHelper.bindCustomTabsService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        customTabActivityHelper.unbindCustomTabsService(this);
        signup.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        customTabActivityHelper.setConnectionCallback(null);
    }
}
