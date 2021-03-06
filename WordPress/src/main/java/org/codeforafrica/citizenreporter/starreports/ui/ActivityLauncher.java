package org.codeforafrica.citizenreporter.starreports.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;

import org.codeforafrica.citizenreporter.starreports.R;
import org.codeforafrica.citizenreporter.starreports.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.codeforafrica.citizenreporter.starreports.models.Blog;
import org.codeforafrica.citizenreporter.starreports.models.Post;
import org.codeforafrica.citizenreporter.starreports.networking.SSLCertsViewActivity;
import org.codeforafrica.citizenreporter.starreports.networking.SelfSignedSSLCertsManager;
import org.codeforafrica.citizenreporter.starreports.ui.accounts.HelpActivity;
import org.codeforafrica.citizenreporter.starreports.ui.accounts.NewAccountActivity;
import org.codeforafrica.citizenreporter.starreports.ui.accounts.NewBlogActivity;
import org.codeforafrica.citizenreporter.starreports.ui.accounts.SignInActivity;
import org.codeforafrica.citizenreporter.starreports.ui.comments.CommentsActivity;
import org.codeforafrica.citizenreporter.starreports.ui.main.SitePickerActivity;
import org.codeforafrica.citizenreporter.starreports.ui.media.MediaBrowserActivity;
import org.codeforafrica.citizenreporter.starreports.ui.media.WordPressMediaUtils;
import org.codeforafrica.citizenreporter.starreports.ui.posts.EditPostActivity;
import org.codeforafrica.citizenreporter.starreports.ui.posts.PagesActivity;
import org.codeforafrica.citizenreporter.starreports.ui.posts.PostsActivity;
import org.codeforafrica.citizenreporter.starreports.ui.posts.StoryBoard;
import org.codeforafrica.citizenreporter.starreports.ui.prefs.BlogPreferencesActivity;
import org.codeforafrica.citizenreporter.starreports.ui.prefs.SettingsActivity;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsActivity;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsSinglePostDetailsActivity;
import org.codeforafrica.citizenreporter.starreports.ui.stats.models.PostModel;
import org.codeforafrica.citizenreporter.starreports.ui.storyboard.StoryBoardActivity;
import org.codeforafrica.citizenreporter.starreports.ui.themes.ThemeBrowserActivity;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.HelpshiftHelper;
import org.wordpress.android.util.HelpshiftHelper.Tag;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ActivityLauncher {

    private static final String ARG_DID_SLIDE_IN_FROM_RIGHT = "did_slide_in_from_right";

    public static void showSitePickerForResult(Activity activity, int blogLocalTableId) {
        Intent intent = new Intent(activity, SitePickerActivity.class);
        intent.putExtra(SitePickerActivity.KEY_LOCAL_ID, blogLocalTableId);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                activity,
                R.anim.activity_slide_in_from_left,
                R.anim.do_nothing);
        ActivityCompat.startActivityForResult(activity, intent, RequestCodes.SITE_PICKER, options.toBundle());
    }

    public static void viewCurrentSite(Context context) {
        Intent intent = new Intent(context, ViewSiteActivity.class);
        slideInFromRight(context, intent);
    }
    public static void viewBlogStats(Context context, int blogLocalTableId) {
        if (blogLocalTableId == 0) return;

        Intent intent = new Intent(context, StatsActivity.class);
        intent.putExtra(StatsActivity.ARG_LOCAL_TABLE_BLOG_ID, blogLocalTableId);
        slideInFromRight(context, intent);
    }

    public static void viewCurrentBlogPosts(Context context) {
        Intent intent = new Intent(context, PostsActivity.class);
        slideInFromRight(context, intent);
    }

    public static void viewCurrentBlogMedia(Context context) {
        Intent intent = new Intent(context, MediaBrowserActivity.class);
        slideInFromRight(context, intent);
    }

    public static void viewCurrentBlogPages(Context context) {
        Intent intent = new Intent(context, PagesActivity.class);
        intent.putExtra(PostsActivity.EXTRA_VIEW_PAGES, true);
        slideInFromRight(context, intent);
    }

    public static void viewCurrentBlogComments(Context context) {
        Intent intent = new Intent(context, CommentsActivity.class);
        slideInFromRight(context, intent);
    }

    public static void viewCurrentBlogThemes(Context context) {
        if (ThemeBrowserActivity.isAccessible()) {
            Intent intent = new Intent(context, ThemeBrowserActivity.class);
            slideInFromRight(context, intent);
        }
    }

    public static void viewBlogSettingsForResult(Activity activity, Blog blog) {
        if (blog == null) return;

        Intent intent = new Intent(activity, BlogPreferencesActivity.class);
        intent.putExtra(BlogPreferencesActivity.ARG_LOCAL_BLOG_ID, blog.getLocalTableBlogId());
        slideInFromRightForResult(activity, intent, RequestCodes.BLOG_SETTINGS);
    }

    public static void viewBlogAdmin(Context context, Blog blog) {
        if (blog == null) return;

        AnalyticsTracker.track(AnalyticsTracker.Stat.OPENED_VIEW_ADMIN);

        Intent intent = new Intent(context, WPWebViewActivity.class);
        intent.putExtra(WPWebViewActivity.AUTHENTICATION_USER, blog.getUsername());
        intent.putExtra(WPWebViewActivity.AUTHENTICATION_PASSWD, blog.getPassword());
        intent.putExtra(WPWebViewActivity.URL_TO_LOAD, blog.getAdminUrl());
        intent.putExtra(WPWebViewActivity.AUTHENTICATION_URL, WPWebViewActivity.getBlogLoginUrl(blog));
        intent.putExtra(WPWebViewActivity.LOCAL_BLOG_ID, blog.getLocalTableBlogId());
        slideInFromRight(context, intent);
    }

    public static void addNewBlogPostOrPageForResult(Activity context, Blog blog, boolean isPage) {
        if (blog == null) return;

        // Create a new post object
        Post newPost = new Post(blog.getLocalTableBlogId(), isPage);
        WordPress.wpDB.savePost(newPost);

        Intent intent = new Intent(context, StoryBoard.class);
        intent.putExtra(EditPostActivity.EXTRA_POSTID, newPost.getLocalTablePostId());
        intent.putExtra(EditPostActivity.EXTRA_IS_PAGE, isPage);
        intent.putExtra(EditPostActivity.EXTRA_IS_NEW_POST, true);
        context.startActivityForResult(intent, RequestCodes.EDIT_POST);
    }

    public static void editBlogPostOrPageForResult(Activity activity, long postOrPageId, boolean isPage) {
        Intent intent = new Intent(activity.getApplicationContext(), StoryBoardActivity.class);
        intent.putExtra(EditPostActivity.EXTRA_POSTID, postOrPageId);
        intent.putExtra(EditPostActivity.EXTRA_IS_PAGE, isPage);
        activity.startActivityForResult(intent, RequestCodes.EDIT_POST);
    }

    public static void addMedia(Activity activity) {
        WordPressMediaUtils.launchPictureLibrary(activity);
    }

    public static void viewAccountSettings(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        slideInFromRight(context, intent);
    }

    public static void viewHelpAndSupport(Context context, Tag origin) {
        Intent intent = new Intent(context, HelpActivity.class);
        intent.putExtra(HelpshiftHelper.ORIGIN_KEY, origin);
        slideInFromRight(context, intent);
    }

    public static void viewSSLCerts(Context context) {
        try {
            Intent intent = new Intent(context, SSLCertsViewActivity.class);
            SelfSignedSSLCertsManager selfSignedSSLCertsManager = SelfSignedSSLCertsManager.getInstance(context);
            String lastFailureChainDescription =
                    selfSignedSSLCertsManager.getLastFailureChainDescription().replaceAll("\n", "<br/>");
            intent.putExtra(SSLCertsViewActivity.CERT_DETAILS_KEYS, lastFailureChainDescription);
            context.startActivity(intent);
        } catch (GeneralSecurityException e) {
            AppLog.e(AppLog.T.API, e);
        } catch (IOException e) {
            AppLog.e(AppLog.T.API, e);
        }
    }

    public static void newAccountForResult(Activity activity) {
        Intent intent = new Intent(activity, NewAccountActivity.class);
        activity.startActivityForResult(intent, SignInActivity.CREATE_ACCOUNT_REQUEST);
    }

    public static void newBlogForResult(Activity activity) {
        Intent intent = new Intent(activity, NewBlogActivity.class);
        intent.putExtra(NewBlogActivity.KEY_START_MODE, NewBlogActivity.CREATE_BLOG);
        activity.startActivityForResult(intent, RequestCodes.CREATE_BLOG);
    }

    public static void showSignInForResult(Activity activity) {
        Intent intent = new Intent(activity, SignInActivity.class);
        activity.startActivityForResult(intent, RequestCodes.ADD_ACCOUNT);
    }

    public static void viewStatsSinglePostDetails(Context context, PostModel post) {
        if (post == null) return;

        Intent statsPostViewIntent = new Intent(context, StatsSinglePostDetailsActivity.class);
        statsPostViewIntent.putExtra(StatsSinglePostDetailsActivity.ARG_REMOTE_POST_OBJECT, post);
        context.startActivity(statsPostViewIntent);
    }

    public static void addSelfHostedSiteForResult(Activity activity) {
        Intent intent = new Intent(activity, SignInActivity.class);
        intent.putExtra(SignInActivity.START_FRAGMENT_KEY, SignInActivity.ADD_SELF_HOSTED_BLOG);
        activity.startActivityForResult(intent, SignInActivity.CREATE_ACCOUNT_REQUEST);
    }

    public static void slideInFromRight(Context context, Intent intent) {
        if (context instanceof Activity) {
            intent.putExtra(ARG_DID_SLIDE_IN_FROM_RIGHT, true);
            Activity activity = (Activity) context;
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                    activity,
                    R.anim.activity_slide_in_from_right,
                    R.anim.do_nothing);
            ActivityCompat.startActivity(activity, intent, options.toBundle());
        } else {
            context.startActivity(intent);
        }
    }

    public static void slideInFromRightForResult(Activity activity, Intent intent, int requestCode) {
        intent.putExtra(ARG_DID_SLIDE_IN_FROM_RIGHT, true);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                activity,
                R.anim.activity_slide_in_from_right,
                R.anim.do_nothing);
        ActivityCompat.startActivityForResult(activity, intent, requestCode, options.toBundle());
    }

    /*
     * called in an activity's finish to slide it out to the right if it slid in
     * from the right when started
     */
    public static void slideOutToRight(Activity activity) {
        if (activity != null
                && activity.getIntent() != null
                && activity.getIntent().hasExtra(ARG_DID_SLIDE_IN_FROM_RIGHT)) {
            activity.overridePendingTransition(R.anim.do_nothing, R.anim.activity_slide_out_to_right);
        }
    }
}
