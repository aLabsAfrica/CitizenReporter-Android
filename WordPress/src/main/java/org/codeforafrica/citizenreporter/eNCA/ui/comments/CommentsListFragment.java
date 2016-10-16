package org.codeforafrica.citizenreporter.eNCA.ui.comments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.codeforafrica.citizenreporter.eNCA.R;
import org.codeforafrica.citizenreporter.eNCA.WordPress;
import org.codeforafrica.citizenreporter.eNCA.models.Blog;
import org.codeforafrica.citizenreporter.eNCA.models.Comment;
import org.codeforafrica.citizenreporter.eNCA.models.CommentList;
import org.codeforafrica.citizenreporter.eNCA.models.CommentStatus;
import org.codeforafrica.citizenreporter.eNCA.ui.EmptyViewMessageType;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.util.helpers.SwipeToRefreshHelper;
import org.wordpress.android.util.helpers.SwipeToRefreshHelper.RefreshListener;
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout;
import org.xmlrpc.android.ApiHelper;
import org.xmlrpc.android.ApiHelper.ErrorType;
import org.xmlrpc.android.XMLRPCFault;

import java.util.HashMap;
import java.util.Map;

public class CommentsListFragment extends Fragment {
    private boolean mIsUpdatingComments = false;
    private boolean mCanLoadMoreComments = true;
    boolean mHasAutoRefreshedComments = false;
    private boolean mHasCheckedDeletedComments = false;

    private ProgressBar mProgressLoadMore;
    private SwipeToRefreshHelper mSwipeToRefreshHelper;
    private ListView mListView;
    private CommentAdapter mCommentAdapter;
    private ActionMode mActionMode;
    private TextView mEmptyView;
    private EmptyViewMessageType mEmptyViewMessageType = EmptyViewMessageType.NO_CONTENT;

    private UpdateCommentsTask mUpdateCommentsTask;

    private OnCommentSelectedListener mOnCommentSelectedListener;

    private static final int COMMENTS_PER_PAGE = 30;

    private ListView getListView() {
        return mListView;
    }

    private CommentAdapter getCommentAdapter() {
        if (mCommentAdapter == null) {
            /*
             * called after comments have been loaded
             */
            CommentAdapter.DataLoadedListener dataLoadedListener = new CommentAdapter.DataLoadedListener() {
                @Override
                public void onDataLoaded(boolean isEmpty) {
                    if (!isAdded())
                        return;

                    if (!isEmpty) {
                        // Hide the empty view if there are already some displayed comments
                        hideEmptyView();
                    } else if (!mIsUpdatingComments && mEmptyViewMessageType.equals(EmptyViewMessageType.LOADING)) {
                        // Change LOADING to NO_CONTENT message
                        updateEmptyView(EmptyViewMessageType.NO_CONTENT);
                    }
                }
            };

            // adapter calls this to request more comments from server when it reaches the end
            CommentAdapter.OnLoadMoreListener loadMoreListener = new CommentAdapter.OnLoadMoreListener() {
                @Override
                public void onLoadMore() {
                    if (mCanLoadMoreComments && !mIsUpdatingComments) {
                        updateComments(true);
                    }
                }
            };

            // adapter calls this when selected comments have changed (CAB)
            CommentAdapter.OnSelectedItemsChangeListener changeListener = new CommentAdapter.OnSelectedItemsChangeListener() {
                @Override
                public void onSelectedItemsChanged() {
                    if (mActionMode != null) {
                        if (getSelectedCommentCount() == 0) {
                            mActionMode.finish();
                        } else {
                            updateActionModeTitle();
                            // must invalidate to ensure onPrepareActionMode is called
                            mActionMode.invalidate();
                        }
                    }
                }
            };

            mCommentAdapter = new CommentAdapter(getActivity(),
                                                 dataLoadedListener,
                                                 loadMoreListener,
                                                 changeListener);
        }
        return mCommentAdapter;
    }

    private boolean hasCommentAdapter() {
        return (mCommentAdapter != null);
    }

    private int getSelectedCommentCount() {
        return getCommentAdapter().getSelectedCommentCount();
    }

    void clear() {
        if (hasCommentAdapter()) {
            getCommentAdapter().clear();
        }
    }

    public void removeComment(Comment comment) {
        if (hasCommentAdapter() && comment != null) {
            getCommentAdapter().removeComment(comment);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setUpListView();
        getCommentAdapter().loadComments();

        Bundle extras = getActivity().getIntent().getExtras();
        if (extras != null) {
            mHasAutoRefreshedComments = extras.getBoolean(CommentsActivity.KEY_AUTO_REFRESHED);
            mEmptyViewMessageType = EmptyViewMessageType.getEnumFromString(extras.getString(
                    CommentsActivity.KEY_EMPTY_VIEW_MESSAGE));
        } else {
            mHasAutoRefreshedComments = false;
            mEmptyViewMessageType = EmptyViewMessageType.NO_CONTENT;
        }

        if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            updateEmptyView(EmptyViewMessageType.NETWORK_ERROR);
            return;
        }

        // Restore the empty view's message
        updateEmptyView(mEmptyViewMessageType);

        if (!mHasAutoRefreshedComments) {
            updateComments(false);
            mSwipeToRefreshHelper.setRefreshing(true);
            mHasAutoRefreshedComments = true;
        }
    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            // check that the containing activity implements our callback
            mOnCommentSelectedListener = (OnCommentSelectedListener) activity;
        } catch (ClassCastException e) {
            activity.finish();
            throw new ClassCastException(activity.toString() + " must implement Callback");
        }
    }

    public void onBlogChanged() {
        mHasCheckedDeletedComments = false;
        if (mUpdateCommentsTask != null) {
            mUpdateCommentsTask.setRetryOnCancelled(true);
            mUpdateCommentsTask.cancel(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.comment_list_fragment, container, false);

        mListView = (ListView) view.findViewById(android.R.id.list);
        mEmptyView = (TextView) view.findViewById(R.id.empty_view);

        // progress bar that appears when loading more comments
        mProgressLoadMore = (ProgressBar) view.findViewById(R.id.progress_loading);
        mProgressLoadMore.setVisibility(View.GONE);

        // swipe to refresh setup
        mSwipeToRefreshHelper = new SwipeToRefreshHelper(getActivity(),
                (CustomSwipeRefreshLayout) view.findViewById(R.id.ptr_layout),
                new RefreshListener() {
                    @Override
                    public void onRefreshStarted() {
                        if (!isAdded()) {
                            return;
                        }
                        if (!NetworkUtils.checkConnection(getActivity())) {
                            mSwipeToRefreshHelper.setRefreshing(false);
                            updateEmptyView(EmptyViewMessageType.NETWORK_ERROR);
                            return;
                        }
                        updateComments(false);
                    }
                });
        return view;
    }

    public void setRefreshing(boolean refreshing) {
        mSwipeToRefreshHelper.setRefreshing(refreshing);
    }

    private void dismissDialog(int id) {
        if (!isAdded())
            return;
        try {
            getActivity().dismissDialog(id);
        } catch (IllegalArgumentException e) {
            // raised when dialog wasn't created
        }
    }

    private void moderateSelectedComments(final CommentStatus newStatus) {
        final CommentList selectedComments = getCommentAdapter().getSelectedComments();
        final CommentList updateComments = new CommentList();

        // build list of comments whose status is different than passed
        for (Comment comment: selectedComments) {
            if (comment.getStatusEnum() != newStatus)
                updateComments.add(comment);
        }
        if (updateComments.size() == 0)
            return;

        if (!NetworkUtils.checkConnection(getActivity()))
            return;

        final int dlgId;
        switch (newStatus) {
            case APPROVED:
                dlgId = CommentDialogs.ID_COMMENT_DLG_APPROVING;
                break;
            case UNAPPROVED:
                dlgId = CommentDialogs.ID_COMMENT_DLG_UNAPPROVING;
                break;
            case SPAM:
                dlgId = CommentDialogs.ID_COMMENT_DLG_SPAMMING;
                break;
            case TRASH:
                dlgId = CommentDialogs.ID_COMMENT_DLG_TRASHING;
                break;
            default :
                return;
        }
        getActivity().showDialog(dlgId);

        CommentActions.OnCommentsModeratedListener listener = new CommentActions.OnCommentsModeratedListener() {
            @Override
            public void onCommentsModerated(final CommentList moderatedComments) {
                if (!isAdded())
                    return;
                finishActionMode();
                dismissDialog(dlgId);
                if (moderatedComments.size() > 0) {
                    getCommentAdapter().clearSelectedComments();
                    getCommentAdapter().replaceComments(moderatedComments);
                } else {
                    ToastUtils.showToast(getActivity(), R.string.error_moderate_comment);
                }
            }
        };

        CommentActions.moderateComments(WordPress.getCurrentLocalTableBlogId(), updateComments, newStatus, listener);
    }

    private void confirmDeleteComments() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.dlg_confirm_trash_comments);
        builder.setTitle(R.string.trash);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.trash_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                deleteSelectedComments();
            }
        });
        builder.setNegativeButton(R.string.trash_no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void deleteSelectedComments() {
        if (!NetworkUtils.checkConnection(getActivity()))
            return;

        final CommentList selectedComments = getCommentAdapter().getSelectedComments();
        getActivity().showDialog(CommentDialogs.ID_COMMENT_DLG_TRASHING);
        CommentActions.OnCommentsModeratedListener listener = new CommentActions.OnCommentsModeratedListener() {
            @Override
            public void onCommentsModerated(final CommentList deletedComments) {
                if (!isAdded())
                    return;
                finishActionMode();
                dismissDialog(CommentDialogs.ID_COMMENT_DLG_TRASHING);
                if (deletedComments.size() > 0) {
                    getCommentAdapter().clearSelectedComments();
                    getCommentAdapter().deleteComments(deletedComments);
                } else {
                    ToastUtils.showToast(getActivity(), R.string.error_moderate_comment);
                }
            }
        };

        CommentActions.moderateComments(WordPress.getCurrentLocalTableBlogId(), selectedComments, CommentStatus.TRASH,
                listener);
    }

    private void setUpListView() {
        ListView listView = this.getListView();
        listView.setAdapter(getCommentAdapter());

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mActionMode == null) {
                    Comment comment = (Comment) getCommentAdapter().getItem(position);
                    if (!getCommentAdapter().isModeratingCommentId(comment.commentID)) {
                        mOnCommentSelectedListener.onCommentSelected(comment.commentID);
                        getListView().invalidateViews();
                    }
                } else {
                    getCommentAdapter().toggleItemSelected(position, view);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // enable CAB if it's not already enabled
                if (mActionMode == null) {
                    if (getActivity() instanceof ActionBarActivity) {
                        ((ActionBarActivity) getActivity()).startSupportActionMode(new ActionModeCallback());
                        getCommentAdapter().setEnableSelection(true);
                        getCommentAdapter().setItemSelected(position, true, view);
                    }
                } else {
                    getCommentAdapter().toggleItemSelected(position, view);
                }
                return true;
            }
        });
    }

    void loadComments() {
        // this is called from CommentsActivity when a comment was changed in the detail view,
        // and the change will already be in SQLite so simply reload the comment adapter
        // to show the change
        getCommentAdapter().loadComments();
    }

    /*
     * get latest comments from server, or pass loadMore=true to get comments beyond the
     * existing ones
     */
    void updateComments(boolean loadMore) {
        if (mIsUpdatingComments) {
            AppLog.w(AppLog.T.COMMENTS, "update comments task already running");
            return;
        } else if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            updateEmptyView(EmptyViewMessageType.NETWORK_ERROR);
            setRefreshing(false);
            return;
        }

        updateEmptyView(EmptyViewMessageType.LOADING);

        mUpdateCommentsTask = new UpdateCommentsTask(loadMore);
        mUpdateCommentsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void setCommentIsModerating(long commentId, boolean isModerating) {
        if (!hasCommentAdapter()) return;

        if (isModerating) {
            getCommentAdapter().addModeratingCommentId(commentId);
        } else {
            getCommentAdapter().removeModeratingCommentId(commentId);
        }
    }

    /*
     * task to retrieve latest comments from server
     */
    private class UpdateCommentsTask extends AsyncTask<Void, Void, CommentList> {
        ErrorType mErrorType = ErrorType.NO_ERROR;
        final boolean mIsLoadingMore;
        boolean mRetryOnCancelled;

        private UpdateCommentsTask(boolean loadMore) {
            mIsLoadingMore = loadMore;
        }

        public void setRetryOnCancelled(boolean retryOnCancelled) {
            mRetryOnCancelled = retryOnCancelled;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mIsUpdatingComments = true;
            if (mIsLoadingMore) {
                showLoadingProgress();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            mIsUpdatingComments = false;
            mUpdateCommentsTask = null;
            if (mRetryOnCancelled) {
                mRetryOnCancelled = false;
                updateComments(false);
            } else {
                mSwipeToRefreshHelper.setRefreshing(false);
            }
        }

        @Override
        protected CommentList doInBackground(Void... args) {
            if (!isAdded())
                return null;

            Blog blog = WordPress.getCurrentBlog();
            if (blog == null) {
                mErrorType = ErrorType.INVALID_CURRENT_BLOG;
                return null;
            }

            // the first time this is called, make sure comments deleted on server are removed
            // from the local database
            if (!mHasCheckedDeletedComments && !mIsLoadingMore) {
                mHasCheckedDeletedComments = true;
                ApiHelper.removeDeletedComments(blog);
            }

            Map<String, Object> hPost = new HashMap<String, Object>();
            if (mIsLoadingMore) {
                int numExisting = getCommentAdapter().getCount();
                hPost.put("offset", numExisting);
                hPost.put("number", COMMENTS_PER_PAGE);
            } else {
                hPost.put("number", COMMENTS_PER_PAGE);
            }

            Object[] params = { blog.getRemoteBlogId(),
                                blog.getUsername(),
                                blog.getPassword(),
                                hPost };
            try {
                return ApiHelper.refreshComments(blog, params);
            } catch (XMLRPCFault xmlrpcFault) {
                mErrorType = ErrorType.UNKNOWN_ERROR;
                if (xmlrpcFault.getFaultCode() == 401) {
                    mErrorType = ErrorType.UNAUTHORIZED;
                }
            } catch (Exception e) {
                mErrorType = ErrorType.UNKNOWN_ERROR;
            }
            return null;
        }

        protected void onPostExecute(CommentList comments) {
            mIsUpdatingComments = false;
            mUpdateCommentsTask = null;
            if (!isAdded()) {
                return;
            }
            if (mIsLoadingMore) {
                hideLoadingProgress();
            }
            mSwipeToRefreshHelper.setRefreshing(false);

            if (isCancelled()) {
                return;
            }

            mCanLoadMoreComments = (comments != null && comments.size() > 0);

            // result will be null on error OR if no more comments exists
            if (comments == null && !getActivity().isFinishing() && mErrorType != ErrorType.NO_ERROR) {
                switch (mErrorType) {
                    case UNAUTHORIZED:
                        if (mEmptyView == null || mEmptyView.getVisibility() != View.VISIBLE) {
                            ToastUtils.showToast(getActivity(), getString(R.string.error_refresh_unauthorized_comments));
                        }
                        updateEmptyView(EmptyViewMessageType.PERMISSION_ERROR);
                        return;
                    default:
                        ToastUtils.showToast(getActivity(), getString(R.string.error_refresh_comments));
                        updateEmptyView(EmptyViewMessageType.GENERIC_ERROR);
                        return;
                }
            }

            if (!getActivity().isFinishing()) {
                if (comments != null && comments.size() > 0) {
                    getCommentAdapter().loadComments();
                } else {
                    updateEmptyView(EmptyViewMessageType.NO_CONTENT);
                }
            }

        }
    }

    public interface OnCommentSelectedListener {
        public void onCommentSelected(long commentId);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (outState.isEmpty()) {
            outState.putBoolean("bug_19917_fix", true);
        }
        super.onSaveInstanceState(outState);
    }

    private void hideEmptyView() {
        if (mEmptyView != null) {
            mEmptyView.setVisibility(View.GONE);
        }
    }

    private void updateEmptyView(EmptyViewMessageType emptyViewMessageType) {
        if (!isAdded()) {
            return;
        }

        if (mEmptyView != null) {
            if (getCommentAdapter().getCount() == 0) {
                int stringId = 0;

                switch (emptyViewMessageType) {
                    case LOADING:
                        stringId = R.string.comments_fetching;
                        break;
                    case NO_CONTENT:
                        stringId = R.string.comments_empty_list;
                        break;
                    case NETWORK_ERROR:
                        stringId = R.string.no_network_message;
                        break;
                    case PERMISSION_ERROR:
                        stringId = R.string.error_refresh_unauthorized_comments;
                        break;
                    case GENERIC_ERROR:
                        stringId = R.string.error_refresh_comments;
                        break;
                }

                mEmptyView.setText(getText(stringId));
                mEmptyViewMessageType = emptyViewMessageType;
                mEmptyView.setVisibility(View.VISIBLE);
            } else {
                mEmptyView.setVisibility(View.GONE);
            }
        }
    }

    public String getEmptyViewMessage() {
        return mEmptyViewMessageType.name();
    }

    /**
     * show/hide progress bar which appears at the bottom when loading more comments
     */
    private void showLoadingProgress() {
        if (isAdded() && mProgressLoadMore != null) {
            mProgressLoadMore.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingProgress() {
        if (isAdded() && mProgressLoadMore != null) {
            mProgressLoadMore.setVisibility(View.GONE);
        }
    }

    /****
     * Contextual ActionBar (CAB) routines
     ***/
    private void updateActionModeTitle() {
        if (mActionMode == null)
            return;
        int numSelected = getSelectedCommentCount();
        if (numSelected > 0) {
            mActionMode.setTitle(Integer.toString(numSelected));
        } else {
            mActionMode.setTitle("");
        }
    }

    private void finishActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    private final class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            mActionMode = actionMode;
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.menu_comments_cab, menu);
            mSwipeToRefreshHelper.setEnabled(false);
            return true;
        }

        private void setItemEnabled(Menu menu, int menuId, boolean isEnabled) {
            final MenuItem item = menu.findItem(menuId);
            if (item == null || item.isEnabled() == isEnabled)
                return;
            item.setEnabled(isEnabled);
            if (item.getIcon() != null) {
                // must mutate the drawable to avoid affecting other instances of it
                Drawable icon = item.getIcon().mutate();
                icon.setAlpha(isEnabled ? 255 : 128);
                item.setIcon(icon);
            }
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            final CommentList selectedComments = getCommentAdapter().getSelectedComments();

            boolean hasSelection = (selectedComments.size() > 0);
            boolean hasApproved = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.APPROVED);
            boolean hasUnapproved = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.UNAPPROVED);
            boolean hasSpam = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.SPAM);
            boolean hasAnyNonSpam = hasSelection && selectedComments.hasAnyWithoutStatus(CommentStatus.SPAM);

            setItemEnabled(menu, R.id.menu_approve,   hasUnapproved || hasSpam);
            setItemEnabled(menu, R.id.menu_unapprove, hasApproved);
            setItemEnabled(menu, R.id.menu_spam,      hasAnyNonSpam);
            setItemEnabled(menu, R.id.menu_trash,     hasSelection);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            int numSelected = getSelectedCommentCount();
            if (numSelected == 0)
                return false;

            int i = menuItem.getItemId();
            if (i == R.id.menu_approve) {
                moderateSelectedComments(CommentStatus.APPROVED);
                return true;
            } else if (i == R.id.menu_unapprove) {
                moderateSelectedComments(CommentStatus.UNAPPROVED);
                return true;
            } else if (i == R.id.menu_spam) {
                moderateSelectedComments(CommentStatus.SPAM);
                return true;
            } else if (i == R.id.menu_trash) {// unlike the other status changes, we ask the user to confirm trashing
                confirmDeleteComments();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            getCommentAdapter().setEnableSelection(false);
            mSwipeToRefreshHelper.setEnabled(true);
            mActionMode = null;
        }
    }
}
