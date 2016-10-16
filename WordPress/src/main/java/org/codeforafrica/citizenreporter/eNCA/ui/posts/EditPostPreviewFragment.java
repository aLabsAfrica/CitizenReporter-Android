package org.codeforafrica.citizenreporter.eNCA.ui.posts;

import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.TextView;

import org.codeforafrica.citizenreporter.eNCA.R;
import org.codeforafrica.citizenreporter.eNCA.models.Post;
import org.codeforafrica.citizenreporter.eNCA.ui.storyboard.StoryBoardActivity;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.WPHtml;

public class EditPostPreviewFragment extends Fragment {
    // TODO: remove mActivity and rely on getActivity()
    private StoryBoardActivity mActivity;
    private WebView mWebView;
    private TextView mTextView;
    private LoadPostPreviewTask mLoadTask;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity = (StoryBoardActivity)getActivity();

        ViewGroup rootView = (ViewGroup) inflater
                .inflate(R.layout.edit_post_preview_fragment, container, false);
        mWebView = (WebView) rootView.findViewById(R.id.post_preview_webview);
        mTextView = (TextView) rootView.findViewById(R.id.post_preview_textview);
        mTextView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mActivity != null) {
                    loadPost();
                }
                mTextView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });


        return rootView;
    }
    private int minim = getMin(mTextView.getWidth(), mTextView.getHeight());

    @Override
    public void onResume() {
        super.onResume();

        if (mActivity != null && !mTextView.isLayoutRequested()) {
            loadPost();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
    }

    public void loadPost() {
        if (mLoadTask == null) {
            mLoadTask = new LoadPostPreviewTask();
            mLoadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private int getMin(int a, int b){
        return Math.min(a, b);
    }

    // Load post content in the background
    private class LoadPostPreviewTask extends AsyncTask<Void, Void, Spanned> {
        @Override
        protected Spanned doInBackground(Void... params) {
            Spanned contentSpannable;

            if (mActivity == null || mActivity.getPost() == null) {
                return null;
            }

            Post post = mActivity.getPost();

            String postTitle = "<h1>" + post.getTitle() + "</h1>";
            String postContent = postTitle + post.getDescription() + "\n\n" + post.getMoreText();

            if (post.isLocalDraft()) {
                contentSpannable = WPHtml.fromHtml(
                        postContent.replaceAll("\uFFFC", ""),
                        mActivity,
                        post,
                        minim

                );
            } else {
                String htmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><html><head><link rel=\"stylesheet\" type=\"text/css\" href=\"webview.css\" /></head><body><div id=\"container\">%s</div></body></html>";
                htmlText = String.format(htmlText, StringUtils.addPTags(postContent));
                contentSpannable = new SpannableString(htmlText);
            }

            return contentSpannable;
        }

        @Override
        protected void onPostExecute(Spanned spanned) {
            if (mActivity != null && mActivity.getPost() != null && spanned != null) {
                if (mActivity.getPost().isLocalDraft()) {
                    mTextView.setVisibility(View.VISIBLE);
                    mWebView.setVisibility(View.GONE);
                    mTextView.setText(spanned);
                } else {
                    mTextView.setVisibility(View.GONE);
                    mWebView.setVisibility(View.VISIBLE);

                    mWebView.loadDataWithBaseURL("file:///android_asset/", spanned.toString(),
                            "text/html", "utf-8", null);
                }
            }

            mLoadTask = null;
        }
    }
}
