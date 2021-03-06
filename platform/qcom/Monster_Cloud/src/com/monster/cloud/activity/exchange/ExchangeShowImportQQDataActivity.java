package com.monster.cloud.activity.exchange;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.monster.cloud.R;
import com.monster.cloud.utils.LoginUtil;
import com.monster.cloud.utils.SystemUtil;
import com.tcl.account.sdkapi.QQLoginListener;
import com.tcl.account.sdkapi.SessionStatusCallback;
import com.tcl.account.sdkapi.Token;
import com.tcl.account.sdkapi.UiAccountHelper;

import org.json.JSONException;
import org.json.JSONObject;

import mst.app.MstActivity;

/**
 * Created by zouxu on 16-11-9.
 */
public class ExchangeShowImportQQDataActivity extends MstActivity {

    private RelativeLayout import_data_layout;

    private UiAccountHelper uiAccountHelper = null;

    private boolean is_should_return = true;
    private String title;
    private ImageView img_bg_ok;
    private ImageView img_bg_qq;
    private TextView text_info;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getIntentData();
        SystemUtil.setStatusBarColor(this,R.color.white);

        setMstContentView(R.layout.exchange_import_data_activity);

        img_bg_ok =(ImageView)findViewById(R.id.img_bg_ok);
        img_bg_qq =(ImageView)findViewById(R.id.img_bg_qq);
        text_info =(TextView)findViewById(R.id.text_info);

        if (is_should_return) {
            if (title != null) {
                getToolbar().setTitle(title);
            } else {
                getToolbar().setTitle(R.string.app_name);
            }
            img_bg_qq.setVisibility(View.VISIBLE);
            img_bg_ok.setVisibility(View.GONE);
            text_info.setText(R.string.str_login_info);
        } else {
            getToolbar().setTitle(R.string.str_one_key_exchange);
            img_bg_qq.setVisibility(View.GONE);
            img_bg_ok.setVisibility(View.VISIBLE);
            text_info.setText(R.string.str_import_qq_data);
        }
        getToolbar().inflateMenu(R.menu.menu_regist_qq);

        import_data_layout = (RelativeLayout) findViewById(R.id.import_data_layout);

        import_data_layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                QQLogIn();

            }
        });

        uiAccountHelper = new UiAccountHelper(this, callback);
        uiAccountHelper.onCreate(savedInstanceState);

    }

    private void getIntentData() {
        Intent i = getIntent();
        if (i != null) {
            is_should_return = i.getBooleanExtra("is_should_return", true);
            title = i.getStringExtra("title");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiAccountHelper.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
        uiAccountHelper.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiAccountHelper.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        uiAccountHelper.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            setResult(RESULT_OK);
            finish();
        }
    }


    private SessionStatusCallback callback = new SessionStatusCallback() {
        @Override
        public void onSuccess(Token token) {

        }

        @Override
        public void onError(int i) {

        }

        @Override
        public void onOAuth(String s) {

        }
    };


    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_regist:
                RegistQQ();
                break;
        }
        return true;
    }

    private void QQLogIn() {
        boolean enable = uiAccountHelper.qqLogin(this, new QQLoginListener() {
            @Override
            public void onSuccess(String s) {
                JSONObject json;
                String openId = "";
                String accessToken = "";
                try {
                    json = new JSONObject(s);
                    openId = json.getString("openid");
                    accessToken = json.getString("access_token");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d("zouxu", "ShowImportQQDataActivity ---- openId " + openId + " token " + accessToken);
                LoginUtil.updateOpenId(ExchangeShowImportQQDataActivity.this, openId);
                LoginUtil.updateToken(ExchangeShowImportQQDataActivity.this, accessToken);
                LoginUtil.saveTCLUserId(ExchangeShowImportQQDataActivity.this);
                Intent i = new Intent(ExchangeShowImportQQDataActivity.this, ShowStartRecorveryActivity.class);
                i.putExtra("is_should_return", is_should_return);
                startActivityForResult(i, 100);
                if (!is_should_return) {
                    finish();
                }

            }

            @Override
            public void onError(int errorCode, String errorMessage, String errorDetail) {
                Toast.makeText(ExchangeShowImportQQDataActivity.this,
                        "onError errorCode:" + errorCode + ",errorMessage:" + errorMessage + ",errorDetail" + errorDetail, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCancel() {
                Toast.makeText(ExchangeShowImportQQDataActivity.this, "onCancel", Toast.LENGTH_LONG).show();
            }
        });
        if (!enable) {
            Toast.makeText(ExchangeShowImportQQDataActivity.this, "QQ Login disabled", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onNavigationClicked(View view) {

        finish();
    }

    private void RegistQQ() {
        Uri uri = Uri.parse("https://ssl.zc.qq.com/phone/index.html");
        Intent it = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(it);
    }

}
