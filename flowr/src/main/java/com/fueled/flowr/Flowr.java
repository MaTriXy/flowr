package com.fueled.flowr;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.AnimRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.fueled.flowr.internal.FlowrDeepLinkHandler;
import com.fueled.flowr.internal.FlowrDeepLinkInfo;
import com.fueled.flowr.internal.TransactionData;
import com.fueled.flowr.internal.TransitionConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by hussein@fueled.com on 31/05/2016.
 * Copyright (c) 2016 Fueled. All rights reserved.
 */
@SuppressWarnings({"WeakerAccess", "UnusedDeclaration"}) // Public API.
public class Flowr implements FragmentManager.OnBackStackChangedListener,
        View.OnClickListener {

    /**
     * To be used as Bundle key for deep links.
     */
    public final static String DEEP_LINK_URL = "DEEP_LINK_URL";

    private final static String KEY_REQUEST_BUNDLE = "KEY_REQUEST_BUNDLE";
    private final static String KEY_FRAGMENT_ID = "KEY_FRAGMENT_ID";
    private final static String KEY_REQUEST_CODE = "KEY_REQUEST_CODE";

    private final static String TAG = Flowr.class.getSimpleName();

    private final FragmentsResultPublisher resultPublisher;
    private final int mainContainerId;

    @Nullable private FlowrScreen screen;
    @Nullable private ToolbarHandler toolbarHandler;
    @Nullable private DrawerHandler drawerHandler;

    @Nullable private Fragment currentFragment;

    private boolean overrideBack;
    private String tagPrefix;

    private List<FlowrDeepLinkHandler> deepLinkHandlers;

    /**
     * Constructor to use when creating a new router for an activity
     * that has no toolbar.
     *
     * @param mainContainerId the id of the container where the fragments should be displayed
     * @param screen          the fragment's parent screen
     */
    public Flowr(@IdRes int mainContainerId, @Nullable FlowrScreen screen,
                 FragmentsResultPublisher resultPublisher) {
        this(mainContainerId, screen, null, null, resultPublisher);
    }

    /**
     * Constructor to use when creating a new router for an activity
     * that has no toolbar.
     *
     * @param mainContainerId the id of the container where the fragments should be displayed
     * @param screen          the fragment's parent screen
     * @param tagPrefix       a custom prefix for the tags to be used for fragments that will be added to
     *                        the backstack.
     * @param resultPublisher the result publish to be used to publish results from fragments
     *                        that where opened for results.
     */
    public Flowr(@IdRes int mainContainerId, @Nullable FlowrScreen screen, @NonNull String tagPrefix,
                 FragmentsResultPublisher resultPublisher) {
        this(mainContainerId, screen, null, null, tagPrefix, resultPublisher);
    }

    /**
     * Constructor to use when creating a new router for an activity
     * that has toolbar and a drawer.
     *
     * @param mainContainerId the id of the container where the fragments should be displayed
     * @param screen          the fragment's parent screen
     * @param toolbarHandler  the {@link ToolbarHandler} to be used to sync toolbar state
     * @param drawerHandler   the {@link DrawerHandler} to be used to sync drawer state
     * @param resultPublisher the result publish to be used to publish results from fragments
     *                        that where opened for results.
     */
    public Flowr(@IdRes int mainContainerId, @Nullable FlowrScreen screen,
                 @Nullable ToolbarHandler toolbarHandler, @Nullable DrawerHandler drawerHandler,
                 FragmentsResultPublisher resultPublisher) {
        this(mainContainerId, screen, toolbarHandler, drawerHandler, "#id-", resultPublisher);
    }

    /**
     * Constructor to use when creating a new router for an activity
     * that has toolbar and a drawer.
     *
     * @param mainContainerId the id of the container where the fragments should be displayed
     * @param screen          the fragment's parent screen
     * @param toolbarHandler  the {@link ToolbarHandler} to be used to sync toolbar state
     * @param drawerHandler   the {@link DrawerHandler} to be used to sync drawer state
     * @param tagPrefix       a custom prefix for the tags to be used for fragments that will be added to
     *                        the backstack.
     * @param resultPublisher the result publish to be used to publish results from fragments
     *                        that where opened for results.
     */
    public Flowr(@IdRes int mainContainerId, @Nullable FlowrScreen screen,
                 @Nullable ToolbarHandler toolbarHandler, @Nullable DrawerHandler drawerHandler,
                 @NonNull String tagPrefix, FragmentsResultPublisher resultPublisher) {
        this.resultPublisher = resultPublisher;
        this.mainContainerId = mainContainerId;
        this.tagPrefix = tagPrefix;
        this.overrideBack = false;

        setRouterScreen(screen);
        setToolbarHandler(toolbarHandler);
        setDrawerHandler(drawerHandler);

        deepLinkHandlers = new ArrayList<>();

        syncScreenState();
    }

    /**
     * Build and return a new ResultResponse instant using the arguments passed in.
     *
     * @param arguments  Used to retrieve the ID and request code for the fragment
     *                   requesting the results.
     * @param resultCode The results code to be returned.
     * @param data       Used to return extra data that might be required.
     * @return a new {@link ResultResponse} instance
     */
    public static ResultResponse getResultsResponse(Bundle arguments, int resultCode, Bundle data) {
        if (arguments == null || !arguments.containsKey(KEY_REQUEST_BUNDLE)) {
            return null;
        }

        ResultResponse resultResponse = new ResultResponse();
        resultResponse.resultCode = resultCode;
        resultResponse.data = data;

        Bundle requestBundle = arguments.getBundle(KEY_REQUEST_BUNDLE);

        if (requestBundle != null) {
            resultResponse.fragmentId = requestBundle.getString(KEY_FRAGMENT_ID);
            resultResponse.requestCode = requestBundle.getInt(KEY_REQUEST_CODE);
        }

        return resultResponse;
    }


    /**
     * Returns the {@link FlowrScreen} used for this router.
     *
     * @return the router screen for this router
     */
    @Nullable
    protected FlowrScreen getRouterScreen() {
        return screen;
    }

    /**
     * Sets the {@link FlowrScreen} to be used for this router.
     *
     * @param flowrScreen the router screen to be used
     */
    public void setRouterScreen(@Nullable FlowrScreen flowrScreen) {
        removeCurrentRouterScreen();
        if (flowrScreen != null) {
            this.screen = flowrScreen;

            if (flowrScreen.getScreenFragmentManager() != null) {
                screen.getScreenFragmentManager().addOnBackStackChangedListener(this);
                setCurrentFragment(retrieveCurrentFragment());
            }
        }
    }

    private void removeCurrentRouterScreen() {
        if (screen != null) {
            screen.getScreenFragmentManager().removeOnBackStackChangedListener(this);
            screen = null;
            currentFragment = null;
        }
    }

    /**
     * Sets the {@link ToolbarHandler} to be used to sync toolbar state.
     *
     * @param toolbarHandler the toolbar handler to be used.
     */
    public void setToolbarHandler(@Nullable ToolbarHandler toolbarHandler) {
        removeCurrentToolbarHandler();

        if (toolbarHandler != null) {
            this.toolbarHandler = toolbarHandler;
            toolbarHandler.setToolbarNavigationButtonListener(this);
        }
    }

    private void removeCurrentToolbarHandler() {
        if (toolbarHandler != null) {
            toolbarHandler.setToolbarNavigationButtonListener(null);
            toolbarHandler = null;
        }
    }

    /**
     * Sets the {@link DrawerHandler} to be used to sync drawer state.
     *
     * @param drawerHandler the drawer handler to be used.
     */
    public void setDrawerHandler(@Nullable DrawerHandler drawerHandler) {
        this.drawerHandler = drawerHandler;
    }

    /**
     * Specify a collection of {@link FlowrDeepLinkHandler} to be used when routing deep link
     * intents replacing all previously set handlers.
     *
     * @param handlers the collection of handlers to be used.
     */
    public void setDeepLinkHandlers(FlowrDeepLinkHandler... handlers) {
        this.deepLinkHandlers.clear();

        if (handlers != null) {
            Collections.addAll(deepLinkHandlers, handlers);
        }
    }

    /**
     * Returns the prefix used for the backstack fragments tag
     *
     * @return the prefix used for the backstack fragments tag
     */
    @NonNull
    protected final String getTagPrefix() {
        return tagPrefix;
    }

    /**
     *
     * @param data  TransactionData used to configure fragment transaction
     * @param <T>   type Fragment & FlowrFragment
     * @return      id Identifier of the committed transaction.
     */
    protected <T extends Fragment & FlowrFragment> int displayFragment(TransactionData<T> data) {
        int identifier = -1;
        try {
            if (screen == null) {
                return identifier;
            }

            injectDeepLinkInfo(data);

            if (data.isClearBackStack()) {
                clearBackStack();
            }

            currentFragment = retrieveCurrentFragment();

            Fragment fragment = data.getFragmentClass().newInstance();
            fragment.setArguments(data.getArgs());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && data.getTransitionConfig() != null) {
                setTransitions(fragment, data.getTransitionConfig());
            }

            FragmentTransaction transaction = screen.getScreenFragmentManager().beginTransaction();

            if (!data.isSkipBackStack()) {
                String id = tagPrefix + screen.getScreenFragmentManager().getBackStackEntryCount();
                transaction.addToBackStack(id);
            }

            setCustomAnimations(transaction, data.getEnterAnim(), data.getExitAnim(), data.getPopEnterAnim(), data.getPopExitAnim());

            if (data.isReplaceCurrentFragment()) {
                transaction.replace(mainContainerId, fragment);
            } else {
                transaction.add(mainContainerId, fragment);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && data.getTransitionConfig() != null &&
                    data.getSharedElements() != null && data.getSharedElements().length > 0) {
                addSharedElements(transaction, data.getSharedElements());
            }

            identifier = transaction.commit();

            if (data.isSkipBackStack()) {
                setCurrentFragment(fragment);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while displaying fragment.", e);
        }
        return identifier;
    }

    /**
     * Parse the intent set by {@link TransactionData#deepLinkIntent} and if this intent contains
     * Deep Link info, update the {@link #currentFragment} and the Transaction data.
     *
     * @param data The Transaction data to extend if Deep link info are found in
     *             the {@link TransactionData#deepLinkIntent}.
     * @param <T>  The generic type for a valid Fragment.
     */
    @SuppressWarnings("unchecked")
    private <T extends Fragment & FlowrFragment> void injectDeepLinkInfo(TransactionData<T> data) {
        Intent deepLinkIntent = data.getDeepLinkIntent();
        if (deepLinkIntent != null) {
            for (FlowrDeepLinkHandler handler : deepLinkHandlers) {
                FlowrDeepLinkInfo info = handler.getDeepLinkInfoForIntent(deepLinkIntent);

                if (info != null) {
                    data.setFragmentClass(info.fragment);
                    Bundle dataArgs = data.getArgs();
                    if (dataArgs != null) {
                        data.getArgs().putAll(info.data);
                    } else {
                        data.setArgs(info.data);
                    }

                    break;
                }
            }
        }
    }

    /**
     * Set transitions to the destination fragment from @{@link TransitionConfig}.
     *
     * @param fragment          The destination Fragment.
     * @param transitionConfig  The transition configuration @{@link TransitionConfig}.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setTransitions(Fragment fragment, TransitionConfig transitionConfig) {
        fragment.setEnterTransition(transitionConfig.enter);
        fragment.setSharedElementEnterTransition(transitionConfig.sharedElementEnter);
        fragment.setExitTransition(transitionConfig.exit);
        fragment.setSharedElementReturnTransition(transitionConfig.sharedElementReturn);
    }

    /**
     * Add shared elements to a Fragment Transaction.
     *
     * @param transaction   The transaction that will.
     * @param views         The shared elements.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void addSharedElements(FragmentTransaction transaction, View[] views) {
        for (View view : views) {
            transaction.addSharedElement(view, view.getTransitionName());
        }
    }

    /**
     * Set a Custom Animation to a Fragment transaction
     *
     * @param transaction  The transaction that will
     * @param enterAnim    The animation resource to be used when the next fragment enters.
     * @param exitAnim     The animation resource to be used when the current fragment exits.
     * @param popEnterAnim The animation resource to be used when the previous fragment enters on back pressed.
     * @param popExitAnim  The animation resource to be used when the current fragment exits on back pressed..
     */
    private void setCustomAnimations(FragmentTransaction transaction, @AnimRes int enterAnim,
                                     @AnimRes int exitAnim, @AnimRes int popEnterAnim, @AnimRes int popExitAnim) {
        transaction.setCustomAnimations(
                enterAnim,
                exitAnim,
                popEnterAnim,
                popExitAnim
        );
    }

    @Nullable
    private Fragment retrieveCurrentFragment() {
        Fragment fragment = null;

        if (screen != null) {
            fragment = screen.getScreenFragmentManager()
                    .findFragmentById(mainContainerId);
        }

        return fragment;
    }

    @Override
    public void onBackStackChanged() {
        setCurrentFragment(retrieveCurrentFragment());
    }

    private void updateVisibilityState(Fragment fragment, boolean shown) {
        if (fragment instanceof FlowrFragment) {
            if (shown) {
                ((FlowrFragment) fragment).onShown();
            } else {
                ((FlowrFragment) fragment).onHidden();
            }
        }
    }

    /**
     * Closes the current activity if the fragments back stack is empty,
     * otherwise pop the top fragment from the stack.
     */
    public void close() {
        overrideBack = true;

        if (screen != null) {
            screen.invokeOnBackPressed();
        }
    }

    /**
     * Closes the current activity if the fragments back stack is empty,
     * otherwise pop the top n fragments from the stack.
     *
     * @param n the number of fragments to remove from the back stack
     */
    public void close(int n) {
        if (screen == null) {
            return;
        }

        int count = screen.getScreenFragmentManager().getBackStackEntryCount();
        if (count > 1) {
            String id = tagPrefix + (screen.getScreenFragmentManager().getBackStackEntryCount() - n);
            screen.getScreenFragmentManager()
                    .popBackStackImmediate(id, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } else {
            close();
        }
    }

    /**
     * Closes the current activity if the fragments back stack is empty,
     * otherwise pop upto fragments with id Identifier from the stack.
     *
     * @param id    Identifier of the committed transaction.
     */
    public void closeUpto(int id) {
        if (screen == null) {
            return;
        }

        int count = screen.getScreenFragmentManager().getBackStackEntryCount();
        if (count > 1) {
            screen.getScreenFragmentManager()
                    .popBackStackImmediate(id, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } else {
            close();
        }
    }

    /**
     * Closes the current activity if the fragments back stack is empty,
     * otherwise pop the top fragment from the stack and publish the results response.
     *
     * @param resultResponse the results response to be published
     */
    public void closeWithResults(ResultResponse resultResponse) {
        closeWithResults(resultResponse, 1);
    }

    /**
     * Closes the current activity if the fragments back stack is empty,
     * otherwise pop the top n fragments from the stack and publish the results response.
     *
     * @param resultResponse the results response to be published
     * @param n              the number of fragments to remove from the back stack
     */
    public void closeWithResults(ResultResponse resultResponse, int n) {
        close(n);

        if (resultResponse != null) {
            resultPublisher.publishResult(resultResponse);
        }
    }

    /**
     * Closes the current activity if the fragments back stack is empty,
     * otherwise pop upto fragments with id Identifier from the stack and publish the results response.
     *
     * @param resultResponse    the results response to be published
     * @param id                Identifier of the committed transaction.
     */
    public void closeUptoWithResults(ResultResponse resultResponse, int id) {
        closeUpto(id);

        if (resultResponse != null) {
            resultPublisher.publishResult(resultResponse);
        }
    }

    /**
     * Clears the fragments back stack.
     */
    public void clearBackStack() {
        if (screen != null) {
            screen.getScreenFragmentManager()
                    .popBackStack(tagPrefix + "0", FragmentManager.POP_BACK_STACK_INCLUSIVE);
            currentFragment = null;
        }
    }

    /**
     * Notify the current fragment of the back press event
     * and see if the fragment will handle it.
     *
     * @return true if the event was handled by the fragment
     */
    public boolean onBackPressed() {
        if (!overrideBack && currentFragment instanceof FlowrFragment &&
                ((FlowrFragment) currentFragment).onBackPressed()) {
            return true;
        }

        overrideBack = false;
        return false;
    }

    public void onNavigationIconClicked() {
        if (!(currentFragment instanceof FlowrFragment &&
                ((FlowrFragment) currentFragment).onNavigationIconClick())) {
            close();
        }
    }

    /**
     * Checks if the current fragment is the home fragment.
     *
     * @return true if the current fragment is the home fragment
     */
    public boolean isHomeFragment() {
        return screen == null || screen.getScreenFragmentManager().getBackStackEntryCount() == 0;
    }

    /**
     * Returns the fragment currently being displayed for this screen,
     *
     * @return the fragment currently being displayed
     */
    @Nullable
    public Fragment getCurrentFragment() {
        return currentFragment;
    }

    private void setCurrentFragment(@Nullable Fragment newFragment) {
        if (currentFragment != newFragment) {
            updateVisibilityState(currentFragment, false);
            currentFragment = newFragment;
            updateVisibilityState(currentFragment, true);
            syncScreenState();
        }
    }

    /**
     * Called by the {@link android.app.Activity#onPostCreate(Bundle)} to update
     * the state of the container screen.
     */
    public void syncScreenState() {
        int screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        int navigationBarColor = -1;

        if (currentFragment instanceof FlowrFragment) {
            screenOrientation = ((FlowrFragment) currentFragment).getScreenOrientation();
            navigationBarColor = ((FlowrFragment) currentFragment).getNavigationBarColor();
        }

        if (screen != null) {
            screen.onCurrentFragmentChanged(getCurrentFragment());
            screen.setScreenOrientation(screenOrientation);
            screen.setNavigationBarColor(navigationBarColor);
        }

        syncToolbarState();
        syncDrawerState();
    }

    private void syncToolbarState() {
        if (toolbarHandler == null) {
            return;
        }

        NavigationIconType iconType = NavigationIconType.HIDDEN;
        String title = null;

        if (currentFragment instanceof FlowrFragment) {
            iconType = ((FlowrFragment) currentFragment).getNavigationIconType();
            title = ((FlowrFragment) currentFragment).getTitle();
        }

        if (iconType == NavigationIconType.CUSTOM) {
            toolbarHandler.setCustomNavigationIcon(((FlowrFragment) currentFragment).getNavigationIcon());
        } else {
            toolbarHandler.setNavigationIcon(iconType);
        }

        toolbarHandler.setToolbarVisible(!(currentFragment instanceof FlowrFragment) ||
                ((FlowrFragment) currentFragment).isToolbarVisible());

        toolbarHandler.setToolbarTitle(title != null ? title : "");
    }

    private void syncDrawerState() {
        if (drawerHandler == null) {
            return;
        }

        drawerHandler.setDrawerEnabled(!(currentFragment instanceof FlowrFragment) ||
                ((FlowrFragment) currentFragment).isDrawerEnabled());
    }

    /**
     * Creates a new {@link Builder} instance to be used to display a fragment
     *
     * @param fragmentClass the class for the fragment to be displayed
     * @return a new {@link Builder} instance
     */
    public <T extends Fragment & FlowrFragment> Builder open(Class<? extends T> fragmentClass) {
        return new Builder<>(fragmentClass);
    }

    /**
     * Creates a new {@link Builder} instance to be used to display a fragment
     *
     * @param deepLinkIntent An Intent to parse and open the appropriate Fragment.
     * @return a new {@link Builder} instance
     */
    public Builder open(Intent deepLinkIntent) {
        return new Builder<>(deepLinkIntent);
    }

    /**
     * Creates a new {@link Builder} instance to be used to display a fragment.
     *
     * @param intent        An intent that contains a possible Deep link.
     * @param fragmentClass The fragment to use if the intent doesn't contain a Deep Link.
     * @param <T>           The proper Fragment type.
     * @return a new {@link Builder} instance
     */
    public <T extends Fragment & FlowrFragment> Builder open(Intent intent, Class<? extends T> fragmentClass) {
        return new Builder<>(intent, fragmentClass);
    }

    /**
     * Creates a new {@link Builder} instance to be used to display a fragment
     *
     * @param path the path of a Fragment annotated with {@link com.fueled.flowr.annotations.DeepLink}
     * @return a new {@link Builder} instance
     */
    public Builder open(String path) {
        Uri uri = Uri.parse(path);
        Intent intent = new Intent();
        intent.setData(uri);
        return open(intent);
    }


    /**
     * The default enter animation to be used for fragment transactions
     *
     * @return the default fragment enter animation
     */
    protected int getDefaultEnterAnimation() {
        return FragmentTransaction.TRANSIT_NONE;
    }

    /**
     * The default exit animation to be used for fragment transactions
     *
     * @return the default fragment exit animation
     */
    protected int getDefaultExitAnimation() {
        return FragmentTransaction.TRANSIT_NONE;
    }

    /**
     * The default pop enter animation to be used for fragment transactions
     *
     * @return the default fragment pop enter animation
     */
    protected int getDefaultPopEnterAnimation() {
        return FragmentTransaction.TRANSIT_NONE;
    }

    /**
     * The default pop exit animation to be used for fragment transactions
     *
     * @return the default fragment pop exit animation
     */
    protected int getDefaultPopExitAnimation() {
        return FragmentTransaction.TRANSIT_NONE;
    }

    @Override
    public void onClick(View view) {
        onNavigationIconClicked();
    }

    public void onDestroy() {
        setRouterScreen(null);
        setToolbarHandler(null);
        setDrawerHandler(null);
    }

    /**
     * This builder class is used to show a new fragment inside the current activity
     */
    public class Builder<T extends Fragment & FlowrFragment> {

        private TransactionData<T> data;

        public Builder(Class<? extends T> fragmentClass) {
            data = new TransactionData<>(fragmentClass, getDefaultEnterAnimation(),
                    getDefaultExitAnimation(), getDefaultPopEnterAnimation(),
                    getDefaultPopExitAnimation());
        }

        public Builder(Intent intent) {
            data = new TransactionData<>(null, getDefaultEnterAnimation(),
                    getDefaultExitAnimation(), getDefaultPopEnterAnimation(),
                    getDefaultPopExitAnimation());
            data.setDeepLinkIntent(intent);
        }

        public Builder(Intent intent, Class<? extends T> fragmentClass) {
            data = new TransactionData<>(fragmentClass, getDefaultEnterAnimation(),
                    getDefaultExitAnimation(), getDefaultPopEnterAnimation(),
                    getDefaultPopExitAnimation());
            data.setDeepLinkIntent(intent);
        }

        /**
         * Sets the construction arguments for fragment to be displayed.
         *
         * @param args the construction arguments for this fragment.
         */
        public Builder setData(Bundle args) {
            data.setArgs(args);
            return this;
        }

        /**
         * Specifies if this fragment should not be added to the back stack.
         */
        public Builder skipBackStack(boolean skipBackStack) {
            data.setSkipBackStack(skipBackStack);
            return this;
        }

        /**
         * Specifies if the fragment manager back stack should be cleared.
         */
        public Builder clearBackStack(boolean clearBackStack) {
            data.setClearBackStack(clearBackStack);
            return this;
        }

        /**
         * Specifies if this fragment should replace the current fragment.
         */
        public Builder replaceCurrentFragment(boolean replaceCurrentFragment) {
            data.setReplaceCurrentFragment(replaceCurrentFragment);
            return this;
        }

        /**
         * Specifies the animations to be used for this transaction.
         *
         * @param enterAnim the fragment enter animation.
         * @param exitAnim  the fragment exit animation.
         */
        public Builder setCustomTransactionAnimation(@AnimRes int enterAnim, @AnimRes int exitAnim) {
            return setCustomTransactionAnimation(enterAnim, FragmentTransaction.TRANSIT_NONE,
                    FragmentTransaction.TRANSIT_NONE, exitAnim);
        }


        /**
         * Set a Custom Animation to a Fragment transaction.
         *
         * @param enterAnim    The animation resource to be used when the next fragment enters.
         * @param exitAnim     The animation resource to be used when the current fragment exits.
         * @param popEnterAnim The animation resource to be used when the previous fragment enters on back pressed.
         * @param popExitAnim  The animation resource to be used when the current fragment exits on back pressed..
         */
        public Builder setCustomTransactionAnimation(@AnimRes int enterAnim, @AnimRes int exitAnim,
                                                     @AnimRes int popEnterAnim, @AnimRes int popExitAnim) {
            data.setEnterAnim(enterAnim);
            data.setExitAnim(exitAnim);
            data.setPopEnterAnim(popEnterAnim);
            data.setPopExitAnim(popExitAnim);
            return this;

        }

        /**
         * Set transition between fragments.
         *
         * @param transitionConfig  builder class for configuring fragment transitions
         * @param sharedElements    array of shared elements
         */
        public Builder setTransition(TransitionConfig transitionConfig, View... sharedElements) {
            data.setTransitionConfig(transitionConfig);
            data.setSharedElements(sharedElements);
            return this;
        }

        /**
         * Don't use any animations for this transaction
         */
        public Builder noTransactionAnimation() {
            return setCustomTransactionAnimation(FragmentTransaction.TRANSIT_NONE,
                    FragmentTransaction.TRANSIT_NONE);
        }

        /**
         * Displays the fragment using this builder configurations.
         *
         * @return id Identifier of the committed transaction.
         */
        public int displayFragment() {
            return Flowr.this.displayFragment(data);
        }

        /**
         * Displays the fragment for results using this builder configurations.
         *
         * @param fragmentId  a unique ID that the fragment requesting the results can be identified by,
         *                    it will be later used to deliver the results to the correct fragment instance.
         * @param requestCode this code will be returned in {@link ResultResponse} when the fragment is closed,
         *                    and it can be used to identify the request from which the results were returned.
         *
         * @return id Identifier of the committed transaction.
         */
        public int displayFragmentForResults(String fragmentId, int requestCode) {
            if (!TextUtils.isEmpty(fragmentId)) {
                if (data.getArgs() == null) {
                    data.setArgs(new Bundle());
                }

                data.getArgs().putBundle(KEY_REQUEST_BUNDLE,
                        getResultRequestBundle(fragmentId, requestCode));
            }

            return Flowr.this.displayFragment(data);
        }

        private Bundle getResultRequestBundle(String fragmentId, int requestCode) {
            Bundle request = new Bundle();
            request.putString(KEY_FRAGMENT_ID, fragmentId);
            request.putInt(KEY_REQUEST_CODE, requestCode);
            return request;
        }
    }

}