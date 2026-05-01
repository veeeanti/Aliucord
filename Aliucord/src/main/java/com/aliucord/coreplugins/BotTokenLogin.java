/**
 *  veeλnti - 2026
 */

package com.aliucord.coreplugins;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.aliucord.Utils;
import com.aliucord.Logger;
import com.aliucord.entities.CorePlugin;
import com.aliucord.patcher.Patcher;
import com.aliucord.patcher.Hook;
import com.aliucord.utils.DimenUtils;
import com.aliucord.views.Button;
import com.discord.app.AppActivity;
import com.discord.app.AppFragment;
import com.discord.models.domain.auth.ModelLoginResult;
import com.discord.stores.StoreAuthentication;
import com.discord.stores.StoreStream;
import com.discord.utilities.view.extensions.ViewExtensions;
import com.discord.widgets.auth.WidgetAuthLanding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.lytefast.flexinput.R;

import java.util.ArrayList;

import kotlin.Unit;

/**
 * Core plugin that adds bot token login functionality to the Discord Android app.
 * Allows users to log in with a bot token directly from the login screen.
 */
public final class BotTokenLogin extends CorePlugin {
    private final Logger logger = new Logger("BotTokenLogin");

    public BotTokenLogin() {
        super(new Manifest("BotTokenLogin"));
        getManifest().description = "Provides functionality to log in with a bot token directly from the login screen";
    }

    /**
     * Fragment that displays the bot token login page.
     */
    public static final class Page extends AppFragment {
        public Page() {
            super(Utils.getResId("widget_auth_login", "layout"));
        }

        @Override
        public void onViewBound(View view) {
            super.onViewBound(view);

            LinearLayout container = view.findViewById(Utils.getResId("auth_login_container", "id"));

            // Remove email input, forgot password, and password manager options
            // We keep these indices correct by removing from the end first
            if (container.getChildCount() > 3) {
                container.removeViewAt(3); // remove use a password manager
                container.removeViewAt(2); // remove forgot password
                container.removeViewAt(1); // remove email input
            }

            TextInputLayout input = (TextInputLayout) container.getChildAt(1);
            if (input != null) {
                input.setHint("Bot Token");
                ViewExtensions.setOnImeActionDone(input, false, e -> {
                    if (e.getText() != null && !e.getText().toString().trim().isEmpty()) {
                        login(e.getText());
                    }
                    return Unit.INSTANCE;
                });
            }

            MaterialButton button = (MaterialButton) container.getChildAt(2);
            if (button != null) {
                button.setOnClickListener(v -> {
                    if (input == null || input.getEditText() == null) return;
                    CharSequence token = input.getEditText().getText();
                    if (token != null && !token.toString().trim().isEmpty()) {
                        login(token);
                    }
                });
            }
        }

        /**
         * Attempts to log in with the provided bot token.
         * @param token The bot token to use for authentication
         */
        public void login(CharSequence token) {
            try {
                String tokenStr = token.toString().trim();

                // Bot tokens don't start with "mfa." or "Bot " prefix
                // They are typically long alphanumeric strings
                boolean isMfa = tokenStr.startsWith("mfa.");

                // For bot tokens, we pass null as the email and authTicket
                // The ModelLoginResult constructor parameters are:
                // (isMfa: Boolean, authTicket: String?, token: String?, email: String?, extraData: List<*>)
                ModelLoginResult loginResult = new ModelLoginResult(
                    isMfa,
                    null, // authTicket
                    tokenStr, // token
                    null, // email
                    new ArrayList<>() // extraData
                );

                StoreAuthentication.access$dispatchLogin(
                    StoreStream.getAuthentication(),
                    loginResult
                );
            } catch (Exception e) {
                logger.error("Failed to login with bot token", e);
            }
        }
    }

    @Override
    public void start(Context appContext) throws Throwable {
        try {
            // Add "Login with Bot Token" button to the auth landing screen
            Patcher.addPatch(
                WidgetAuthLanding.class.getDeclaredMethod("onViewBound", View.class),
                new Hook(param -> {
                    try {
                        Context context = ((WidgetAuthLanding) param.thisObject).requireContext();
                        RelativeLayout view = (RelativeLayout) param.args[0];
                        LinearLayout container = (LinearLayout) view.getChildAt(1);

                        int padding = DimenUtils.dpToPx(18);
                        Button btn = new Button(context);
                        btn.setPadding(0, padding, 0, padding);
                        btn.setText("Login using Bot Token");
                        btn.setTextSize(16.0f);

                        // Set appropriate background color based on theme
                        String theme = StoreStream.getUserSettingsSystem().getTheme();
                        int colorRes;
                        if ("light".equals(theme)) {
                            colorRes = R.c.uikit_btn_bg_color_selector_secondary_light;
                        } else {
                            colorRes = R.c.uikit_btn_bg_color_selector_secondary_dark;
                        }
                        btn.setBackgroundColor(context.getResources().getColor(colorRes, null));

                        btn.setOnClickListener(v -> Utils.openPage(v.getContext(), Page.class));

                        container.addView(btn);
                    } catch (Exception e) {
                        logger.error("Failed to add bot token login button", e);
                    }
                })
            );

            // Patch to allow the bot token login page to be displayed
            Patcher.addPatch(
                AppActivity.class,
                "g",
                new Class<?>[]{ ArrayList.class },
                new Hook(param -> {
                    try {
                        boolean result = (boolean) param.getResult();
                        AppActivity activity = (AppActivity) param.thisObject;
                        if (!result && activity.d().equals(Page.class)) {
                            param.setResult(true);
                        }
                    } catch (Exception e) {
                        logger.error("Failed in AppActivity patch", e);
                    }
                })
            );
        } catch (Exception e) {
            logger.error("Failed to initialize BotTokenLogin plugin", e);
        }
    }

    @Override
    public void stop(Context context) {
        // Cleanup if needed
    }
}
