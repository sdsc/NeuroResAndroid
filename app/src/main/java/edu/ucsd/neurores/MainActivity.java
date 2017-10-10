package edu.ucsd.neurores;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.stetho.Stetho;
import com.facebook.stetho.inspector.elements.android.MethodInvoker;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import javax.net.ssl.SSLSocket;

//TODO Handle the errors in HTTP calls
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, DrawerLayout.DrawerListener, ExpandableListView.OnGroupClickListener, ExpandableListView.OnChildClickListener {

    public static final int UNREAD_MENU_GROUP = 0;
    public static final int STAFF_MENU_GROUP = 1;
    public static final int PRIVATE_MENU_GROUP = 2;

    public static final String PREV_CONVERSATION_ID = "previousConversationID";
    public static final String CONVERSATION_ID = "conversationID";


    Toolbar toolbar = null;
    //  This adapter controls the Navigation Drawer's views and data
    private NavDrawerAdapter navDrawerAdapter;
    // The list view in the Navigation Drawer
    private ExpandableListView drawerListView;
    // The fragment that holds the messages of the selected user
    private Fragment currentFragment;
    /* Request for when the search activity is launched by clicking "Search"
       in the navigation drawer */
    private int SEARCH_USER_REQUEST = 1;

    /* Boolean used to keep track of when a different user's messages needs to
       be loaded */
    private boolean needToChangeFragment = false;

    /* Contains all the users that that there are converstaions with */
    public HashMap<Long,User> userList;
    public HashMap<Long,Conversation> currentConversations;
    /* The currently selected user */
    public Conversation selectedConversation;

    public User loggedInUser;
    Toast mostRecentToast;

    private WebSocket socket;
    private BroadcastReceiver screenStateReceiver;
    boolean isPaused;
    boolean screenIsOn;
    String queuedToastMessage;
    private TextView toolbarTitle;
    private LinearLayout warningBanner;

    MessageDatabaseHelper messageDatabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeVariables();

        if(getToken() == null ||  (! isConnectedToNetwork() && messageDatabaseHelper.databaseIsEmpty())){
            goToLogin();
            return;
        }

        setUp();
    }

    private void setUp(){
        if(getIntent().hasExtra(CONVERSATION_ID)){
            setPreviousConversationID(getIntent().getLongExtra(CONVERSATION_ID, -1));
            Log.v("taggy", "previous set");
        }


        registerReceiverForScreen();
        setupToolbar();
        initializeDrawer();
        loadData();
    }

    private void initializeDrawer() {
        drawerListView = (ExpandableListView) findViewById(R.id.nav_view);
        navDrawerAdapter = new NavDrawerAdapter(this);
        drawerListView.setAdapter(navDrawerAdapter);
        drawerListView.setOnGroupClickListener(this);
        drawerListView.setOnChildClickListener(this);

        for (int i = 0; i < navDrawerAdapter.getGroupCount(); i++){
            drawerListView.expandGroup(i);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        drawer.addDrawerListener(this);
        toggle.syncState();
    }

    private void setupToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null){
            //getSupportActionBar().setDisplayShowTitleEnabled(true);
            //getSupportActionBar().setTitle(getResources().getString(R.string.welcome));
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            toolbarTitle = (TextView) toolbar.findViewById(R.id.toolbar_title);
            toolbarTitle.setText(getResources().getString(R.string.welcome));

        }
    }

    private void initializeVariables() {
        isPaused = false;
        screenIsOn = true;
        queuedToastMessage = null;
        currentConversations = new HashMap<>();
        userList = new HashMap<>();
        messageDatabaseHelper = new MessageDatabaseHelper(this);

        //This is used to view the sql data base by going to chrome://inspect on a browser
        Stetho.initializeWithDefaults(this);


        warningBanner = (LinearLayout) findViewById(R.id.warning_banner);
        warningBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBannerClicked();
            }
        });

        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onBannerClicked() {
        RequestWrapper.OnCompleteListener ocl = new RequestWrapper.OnCompleteListener() {
            @Override
            public void onComplete(String s) {
                hideMainElements();
                updateNavDrawer();
            }

            @Override
            public void onError(String s) {
                showToast(getResources().getString(R.string.no_connection), Toast.LENGTH_LONG);
            }
        };


        connectSocket(ocl);
    }

    private void logFireBaseToken() {
        if(FirebaseInstanceId.getInstance().getToken() != null)
            Log.d("token", FirebaseInstanceId.getInstance().getToken());
    }

        private void registerReceiverForScreen() {
       screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch(intent.getAction()){
                    case Intent.ACTION_SCREEN_ON:
                        onScreenTurnedOn();
                        break;
                    case Intent.ACTION_SCREEN_OFF:
                        onScreenTurnedOff();
                        break;
                }

            }
        };
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, screenStateFilter);
    }

    private void onScreenTurnedOn(){
        Log.v("sockett","Screen On");
        screenIsOn = true;
        if(! isPaused){
            connectSocket();
            reloadCurrentFragment();
            showQueuedToast();
        }
    }

    private void onScreenTurnedOff(){
        Log.v("socket","Screen Off");
        screenIsOn = false;
    }

    private void showQueuedToast(){
        if(queuedToastMessage != null){
            showToast(queuedToastMessage, Toast.LENGTH_SHORT);
            queuedToastMessage = null;
        }
    }

    private void unregisterReceiverForScreen(){
        try{
            unregisterReceiver(screenStateReceiver);
        }catch (IllegalArgumentException e){
            // Receiver is not registered. Not a problem
        }
    }

    private boolean isConnectedToNetwork() {
        final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();

        return activeNetwork != null && activeNetwork.isConnected();
    }

    public void checkServerIsOnline(RequestWrapper.OnHTTPRequestCompleteListener onCompleteListener){
        final Context context = this;
        RequestWrapper.checkServerIsOnline(this,  onCompleteListener);
    }

    @Override
    protected void onPause() {
        isPaused = true;
        closeSocket();
        //hideMainElements();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closeSocket();
        unregisterReceiverForScreen();
        super.onDestroy();
    }

    public void setupFragmentAndSocket(){
        invalidateNavigationDrawer();
        setInitialFragment();
        connectSocket();
    }

    protected String getToken(){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        return sPref.getString(LoginActivity.TOKEN, null);
    }


    protected void clearToken(){
        SharedPreferences sp = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(LoginActivity.TOKEN);
        editor.commit();
    }

    protected boolean hasPreviousConversation(){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        return sPref.getLong(PREV_CONVERSATION_ID, -1) != -1;
    }

    protected boolean hasOngoingConversations(){
        return currentConversations.size() > 0;
    }

    protected long getPreviousConversationID(){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        return sPref.getLong(PREV_CONVERSATION_ID, -1);
    }

    protected void setPreviousConversationID(long newID){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sPref.edit();
        editor.putLong(PREV_CONVERSATION_ID , newID);
        editor.commit();
    }

    private String getUsername(){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        return sPref.getString(LoginActivity.NAME, null);
    }

    private MainFragment startMainFragment(){
        MainFragment mFrag = new MainFragment();
        //mFrag.setupSocket(this);
        Bundle i = new Bundle();
        i.putString("token", getToken());
        mFrag.setArguments(i);
        return mFrag;
    }

    public void setKeyboardPushing(){

    }

    private MainFragment loadOnboardingFragment(){
        MainFragment mFrag = new MainFragment();
        Bundle i = new Bundle();
        i.putString("token", getToken());
        i.putBoolean("hasConversation", false);
        mFrag.setArguments(i);
        return mFrag;
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        // Close the drawer if its open
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id){
            case R.id.action_wipe_thread:
                if(currentFragment instanceof MainFragment){
                    wipeAlert();
                }else{
                   Log.v("taggy", "Conversation wipe requested when currentFragment is not MainFragment");
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        isPaused = false;
        // If the returning activity was the search activity

        if(requestCode == SEARCH_USER_REQUEST && ! isConnectedToNetwork()){
            showToast(getResources().getString(R.string.reconnect_to_start_conversation), Toast.LENGTH_LONG);
            return;
        }

        if(requestCode == SEARCH_USER_REQUEST && resultCode == Activity.RESULT_OK){

            // Get the username and id of the newly searched user
            long searchedID = data.getLongExtra("CONVERSATION_ID", -1);
            long[] userIDs = data.getLongArrayExtra("USERS_IDS");
            if(searchedID != -1 && userIDs.length > 0){

                // Deselect the previously selected user (change the background
                // color and selectedUser)
                if(selectedConversation != null){
                    selectedConversation.select();
                }

            /* Set selectedUser */
                if(currentConversations.containsKey(searchedID)) {
                    selectedConversation = currentConversations.get(searchedID);
                }else{
                    Conversation newConversation = new Conversation(searchedID, this);
                    for(long l : userIDs){
                        if(userList.containsKey(l)){
                            newConversation.addUser(userList.get(l));
                        }else{
                            Log.v("warning", "Error: User with id of " + l + " was said to be in conv "
                                    + searchedID + " but was not found in list of users");
                        }
                    }
                    currentConversations.put(searchedID, newConversation);
                    selectedConversation = newConversation;
                }

                // Tell the main activity that the fragment needs to be changed
                needToChangeFragment = true;
            }
        }

    }

    @Override
    protected void onResume() {
        hideMainElements();
        isPaused = false;

        super.onResume();


        if(isConnectedToNetwork()){
            hideWarningBanner();
        }else{
            showMainElements();
            showWarningBanner();
        }

        // Check if the main fragment needs to be changed
        if(needToChangeFragment){
            if(selectedConversation.viewInNavDrawer == null) {
                // Add a view to the navigation bar for the new user
                addToNavBar(PRIVATE_MENU_GROUP, selectedConversation);
            }else{
                // Highlight the view that is already in the nav bar
                selectedConversation.select();
                //selectedUser.viewInNavDrawer.setBackgroundColor(getResources().getColor(R.color.selected));
            }
            changeFragment();
            needToChangeFragment = false;
        }else if(currentFragment != null){
            //reloadCurrentFragment();
            updateNavDrawer();
        }
        connectSocket();

        hideSoftKeyboard();
    }

    /**
     * The method called when the search button in the nav drawer is clicked.
     * Starts the search activity "SearchActivity" for a result
     * @param view the view that was clicked on
     */
    public void searchOnClick(View view) {
        if(isConnectedToNetwork()){
            Intent startSearch = new Intent(MainActivity.this, SearchActivity.class);
            startSearch.putExtra("token", getToken());
            startActivityForResult(startSearch, SEARCH_USER_REQUEST);
        }else{
            showToast(getString(R.string.reconnect_search), Toast.LENGTH_LONG);
        }
    }

    /**
     * The method called when a user is clicked in the nav drawer is clicked.
     * Deselects the previously selected user and selects the clicked on user.
     * @param v user clicked on in the nav drawer
     */
    public void onViewClicked(View v) {
        //Get the id of the user that was clicked on
        if(v.getTag(R.id.CONVERSATION) != null){
            onConversationClick(v, (Long) v.getTag(R.id.CONVERSATION));
        }else if(v.getTag(R.id.USER) != null){
            onUserClick((Long) v.getTag(R.id.USER));
        }else if(v.getTag(R.id.STAFFGROUP) != null){
            toggleVisibility(v);
            navDrawerAdapter.toggleIsExpanded((int) v.getTag(R.id.STAFFGROUP));
        }
        v.invalidate();
    }

    private void onUserClick(long user_id){
        for(Conversation conversation: currentConversations.values()){
            if(conversation.getNumberOfUsers() == 1 && conversation.getUserAtIndex(0).getID() == user_id){
                onConversationClick(conversation.viewInNavDrawer, conversation.getID());
                return;
            }
        }
        ArrayList<Long> userIDs = new ArrayList<>();
        userIDs.add(user_id);
        createConversation(userIDs,PRIVATE_MENU_GROUP , true, 0);
    }

    public void wipeAlert(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Delete messages with " + selectedConversation.getName() + "?");

        alertDialogBuilder
                .setMessage(R.string.wipe_messages_message)
                .setCancelable(true)
                .setPositiveButton("Yes",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        wipeConversation(selectedConversation.getID(), true);
                    }
                })
                .setNegativeButton("No",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void wipeConversation(long conversationID, boolean alertServer){
        if(currentFragment instanceof MainFragment && ((MainFragment)currentFragment).conversation.getID() == conversationID){
            ((MainFragment)currentFragment).wipeConversation(alertServer);
        }

        messageDatabaseHelper.removeAllMessagesInConversation(conversationID);
    }

    private void createConversation(List<Long> userIDs, final int groupID, final boolean changeFragment, final int numOfUnseen){
        RequestWrapper.CreateConversation(this, userIDs, getToken(), new RequestWrapper.OnHTTPRequestCompleteListener() {

            public void onComplete(String s) {
                if(s == null){
                    //indicates the http request returned null, and something went wrong. have them login again
                    Intent i = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(i);
                    finish();
                    return;
                }

                try {
                    //TODO Use jsonconverter
                    JSONObject jo = new JSONObject(s);
                    JSONArray users = jo.getJSONArray("user_ids");
                    long id = jo.getLong("conv_id");

                    Conversation conversation = new Conversation(id, MainActivity.this);
                    for(int i = 0; i < users.length(); i++){
                        id = users.getLong(i);
                        if(id != loggedInUser.getID())
                            conversation.addUser(userList.get(id));
                    }
                    conversation.setNumOfUnread(numOfUnseen);
                    currentConversations.put(conversation.getID(), conversation);
                    Log.v("taggy", "Adding to nav bar");
                    addToNavBar(groupID, conversation);

                    long conversationID = conversation.getID();
                    List<Long> members = conversation.getUserIDs();
                    long unseen = conversation.getNumOfUnread();
                    messageDatabaseHelper.insertConversation(conversationID, members, -1, unseen);

                    if(changeFragment){
                        onConversationClick(conversation.viewInNavDrawer, conversation.getID());
                    }
                    Log.v("taggy", "done creating");

                } catch (JSONException e) {
                    Log.v("taggy", "Fail");
                    Log.v("taggy",  e.getMessage() + "");
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int i) {
                Log.v("taggy", "Error creating conversation");
                if(i == 401){
                    showToast(getString(R.string.cred_expired), Toast.LENGTH_LONG);
                    logout(null);
                    return;
                }
                showToast(getString(R.string.reconnect_to_start_conversation), Toast.LENGTH_LONG);
            }
        });
    }

    public void onNewConversationDetected(final long conversationID){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RequestWrapper.UpdateConversations(getApplication(), getToken(), new RequestWrapper.OnHTTPRequestCompleteListener() {
                    @Override
                    public void onComplete(String s) {
                        if(s == null){
                            //indicates the http request returned null, and something went wrong. have them login again
                            Intent i = new Intent(MainActivity.this, LoginActivity.class);
                            startActivity(i);
                            finish();
                            return;
                        }
                        List<Conversation> conversations = JSONConverter.toConversationList(s, userList);

                        // Find the newly detected conversation
                        Conversation newConversation = null;
                        for(Conversation c: conversations){
                            if(c.getID() == conversationID){
                                newConversation = c;
                            }
                        }

                        if(newConversation == null){
                            Log.v("error ","conversation with ID of " + conversationID + " was not found in the list of conversations");
                            return;
                        }
                        Log.v("taggy", "new conversation found! Creating it");
                        createConversation(newConversation.getUserIDs(), UNREAD_MENU_GROUP, false, 1);
                    }

                    @Override
                    public void onError(int i) {
                        if(i == 401){
                            showToast(getString(R.string.cred_expired), Toast.LENGTH_LONG);
                            logout(null);
                            return;
                        }
                        Log.v("taggy", "Error updating conversations");
                        logout(null);
                    }
                });
            }
        });

    }

    private void onConversationClick(View v, long conversation_id){
        if(selectedConversation != null && selectedConversation.getID() == conversation_id){
            closeDrawer();
            return;
        }

        if(currentConversations.containsKey(conversation_id)){ //a conversation was clicked, and we're about to load it
            //Deselect previous
            if(selectedConversation != null){
                selectedConversation.deselect();
            }
            // Select clicked on user
            selectedConversation = currentConversations.get(conversation_id);;
            selectedConversation.viewInNavDrawer = v;
            selectedConversation.select();
        }
        // Reset the input fields and hide it
        hideSoftKeyboard();

        changeFragment();
    }

    /**
     * Add a view to the navigation drawer for the newUser
     * @param conversation the user to have a view added for
     */
    private void addToNavBar(int groupID, Conversation conversation){
        navDrawerAdapter.addConversation(groupID, conversation);
        drawerListView.invalidateViews();
    }

    /**
     * Change the messages in the fragment to be the messages of selectedUser
     */
    private void changeFragment(){

        if(isConnectedToNetwork()){
            hideMainElements();
        }

        if(selectedConversation != null){
            currentFragment = startMainFragment();

            MainFragment mainFragment = (MainFragment) currentFragment;

            mainFragment.conversation = selectedConversation;
            mainFragment.userName = loggedInUser.getName();
            android.support.v4.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, currentFragment);
            fragmentTransaction.commit();
            mainFragment.loadMessages(this, selectedConversation, userList);
            toolbarTitle.setText(selectedConversation.getName());
            RecyclerView recyclerView = (RecyclerView)findViewById(R.id.recycler_view);
            if(recyclerView != null){
                recyclerView.scrollToPosition(recyclerView.getAdapter().getItemCount() - 1);
            }
            updateMostRecentConversation(selectedConversation.getID());
        }else{
            Log.v("taggy", "Showing pdf");
            currentFragment = new PDFFragment();
            android.support.v4.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, currentFragment);
            fragmentTransaction.commit();
            toolbarTitle.setText("PDF");
            showMainElements();
        }


        closeDrawer();
        updateFrag();
    }

    private void closeDrawer() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    private void reloadCurrentFragment(){
        if(currentFragment != null){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(selectedConversation == null){
                        showMainElements();
                    }else{
                        hideMainElements();
                        changeFragment();
                        drawerListView.invalidateViews();
                    }

                }
            });
        }
    }

    private void updateFrag(){
        if(socket != null){
            socket.updateFrag(currentFragment);
        }
    }

    /**
     * Change the messages in the fragment to be the messages of selectedUser
     */
    private void setInitialFragment(){

        if(isNewUser()){
            currentFragment = loadOnboardingFragment();
            android.support.v4.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, currentFragment);
            fragmentTransaction.commit();
            return;
        }

        long conversationID = getConversationIDForInitialLoad();



        selectedConversation = currentConversations.get(conversationID);
        if(selectedConversation == null){

            if(hasPreviousConversation()){
                setPreviousConversationID(-1);
                setInitialFragment();
                return;
            }

            Log.v("warning", "User has ongoing conversations and no previous conversation but cannot load the first ongoing conversation");
            logout(null);
            finish();
            return;
        }

        assert currentFragment instanceof MainFragment;

        currentFragment = startMainFragment();

        MainFragment mainFragment = (MainFragment) currentFragment;

        mainFragment.conversation = selectedConversation;
        mainFragment.userName = loggedInUser.getName();
        android.support.v4.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, mainFragment);
        fragmentTransaction.commit();
        mainFragment.loadMessages(this, selectedConversation, userList);
        if(getSupportActionBar() != null){
            toolbarTitle.setText(selectedConversation.getName());
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        invalidateNavigationDrawer();
    }

    private long getConversationIDForInitialLoad(){
        if(hasOngoingConversations() && ! hasPreviousConversation()){
            Log.v("taggy", "Has ongoing");
            return getFirstConversationID();
        }else{
            Log.v("taggy", "Got previous");
            return getPreviousConversationID();
        }


    }

    private long getFirstConversationID(){
        if(getNumOfPrivateConversations() > 0){
            return getFirstPrivateConversationID();
        }else{
            return getFirstUnreadConversationID();
        }
    }

    private long getFirstPrivateConversationID(){
        if(getNumOfPrivateConversations() > 0){
            NavDrawerItem item = navDrawerAdapter.getChild(getGroupPosition(PRIVATE_MENU_GROUP), 0);
            return item.getID();
        }else{
         return -1;
        }
    }

    private long getFirstUnreadConversationID(){
        if(getNumOfUnreadConversations() > 0){
            NavDrawerItem item = navDrawerAdapter.getChild(getGroupPosition(UNREAD_MENU_GROUP), 0);
            return item.getID();
        }else{
            return -1;
        }
    }

    private int getNumOfPrivateConversations(){
        return navDrawerAdapter.getChildrenCount(getGroupPosition(PRIVATE_MENU_GROUP));
    }

    private int getNumOfUnreadConversations(){
        return navDrawerAdapter.getChildrenCount(getGroupPosition(UNREAD_MENU_GROUP));
    }

    private int getTotalNumOfConversations(){
        return getNumOfPrivateConversations() + getNumOfUnreadConversations();
    }

    /**
     * Toggle the visibility of the given view between "gone" and "visible"
     * @param v the view that will have its visibility toggled
     */
    public void toggleVisibility(View v){
        View innerLayout = v.findViewById(R.id.inner_layout);
        int newVisibility;
        int imageID;
        if(innerLayout.getVisibility() == LinearLayout.VISIBLE){
            newVisibility = LinearLayout.GONE;
            imageID = R.drawable.expander;
        }else{
            newVisibility = LinearLayout.VISIBLE;
            imageID = R.drawable.contrator;
        }
        innerLayout.setVisibility(newVisibility);

        ImageView iv = (ImageView) v.findViewById(R.id.expander);
        iv.setImageResource(imageID);
    }

    public boolean isNewUser(){
        return (! hasPreviousConversation()) && (! hasOngoingConversations());
    }

    /**
     * Hides the soft keyboard
     */
    public void hideSoftKeyboard() {
        if(getCurrentFocus()!= null) {
            InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }


    private void addDepartment(String name){
        navDrawerAdapter.addDepartment(name);
    }

    private void addUserToDepartment(String departmentName, User newUser){
        navDrawerAdapter.addUserToDepartment(departmentName,newUser);
        userList.put(newUser.getID(), newUser);
    }

    private void loadData(){
        final MainActivity mainActivity = this;
        hideMainElements();
        // Load data from server
        RequestWrapper.UpdateUsers(this, getToken(), new RequestWrapper.OnHTTPRequestCompleteListener() {

            public void onComplete(String s) {
                if(s == null){
                    goToLogin();
                    return;
                }
                List<User> users = JSONConverter.toUserList(s, mainActivity);
                messageDatabaseHelper.insertUsers(users);
                onUsersLoaded(users);
            }


            public void onError(int i) {
                Log.v("taggy", "Error while loading data");
                if(i == 401){
                    showToast(getString(R.string.cred_expired), Toast.LENGTH_LONG);
                    logout(null);
                    return;
                }else if(messageDatabaseHelper.getUserListJSON() != null){
                    List<User> users = messageDatabaseHelper.getUserList();
                    for(User u: users){
                        u.setContext(MainActivity.this);
                    }
                    onUsersLoaded(users);
                    return;
                }
                logout(null);
            }
        });
    }

    private void updateNavDrawer(){
        RequestWrapper.UpdateConversations(this, getToken(), new RequestWrapper.OnHTTPRequestCompleteListener() {
            public void onComplete(final String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(s == null){
                            goToLogin();
                            return;
                        }
                        List<Conversation> conversations = JSONConverter.toConversationList(s, userList);

                        //called update conversation without the context
                        //needs to be connected here now
                        for(Conversation c: conversations){
                            c.setContext(MainActivity.this);
                        }

                        List<Conversation> newConversations = new ArrayList<Conversation>();
                        for( Conversation conversation : conversations){
                            if(conversation.getNumOfUnread() > 0){
                                Conversation actualConversation = currentConversations.get(conversation.getID());
                                if(actualConversation == null){
                                    newConversations.add(conversation);
                                }else{
                                    actualConversation.setNumOfUnread(conversation.getNumOfUnread());
                                    moveConversationToUnread(actualConversation);
                                }
                            }
                        }
                        messageDatabaseHelper.insertConversations(conversations);
                        populateUnread(newConversations);
                        moveAllOnlineConversationsUp();
                        reloadCurrentFragment();
                    }
                });


            }

            @Override
            public void onError(int i) {
                Log.v("taggy", "Error  updating nav drawer");
                if(i == 401){
                    showToast(getString(R.string.cred_expired), Toast.LENGTH_LONG);
                    logout(null);
                    return;
                }
                showMainElements();
                //logout(null);
            }
        });
    }

    public void onUsersLoaded(List<User> users){
        String username = getUsername();
        for(User u : users){
            u.setContext(this);
            if(u.getName().equals(username)){
                loggedInUser = u;
                setNameInNavDrawer();
            }
        }

        populateStaff(users);
        initializeConversations();
    }

    private void initializeConversations() {
        final MainActivity mainActivity = this;
        RequestWrapper.UpdateConversations(this, getToken(), new RequestWrapper.OnHTTPRequestCompleteListener() {
            public void onComplete(String s) {
                if(s == null){
                    goToLogin();
                    return;
                }
                List<Conversation> conversations = JSONConverter.toConversationList(s, userList);

                //called update conversation without the context
                //needs to be connected here now
                for(Conversation c: conversations){
                    c.setContext(MainActivity.this);
                }
                populateConversations(conversations);
                messageDatabaseHelper.insertConversations(conversations);

                onLoadComplete();
            }

            @Override
            public void onError(int i) {
                Log.v("taggy", "Error initializing conversations");
                if(i == 401){
                    showToast(getString(R.string.cred_expired), Toast.LENGTH_LONG);
                    logout(null);
                    return;
                }
                List<Conversation> conversations = messageDatabaseHelper.getConversationsList(mainActivity);
                if(conversations != null){
                    populateConversations(conversations);
                    onLoadComplete();
                }
            }
        });
    }

    private void setNameInNavDrawer() {
        TextView nameInSettingsView = (TextView) findViewById(R.id.username_in_settings_text_view);
        nameInSettingsView.setText(loggedInUser.getName());
    }

    public void populateStaff(List<User> users){
        TreeSet<String> departments = new TreeSet<String>();
        for(User u : users){
            // Do not include the dev department
            if(!u.userType.equals("dev")){
                departments.add(u.userType);
            }
        }

        if(departments.size() > 0){
            String curr = departments.first();
            while(curr != null){
                addDepartment(curr);
                curr = departments.higher(curr);
            }
        }


        for(User u : users){
            if(u != loggedInUser){
                addUserToDepartment(u.userType, u);
            }
        }

    }

    public void populatePrivate(List<Conversation> conversations){
        for(Conversation c : conversations){
            if( ! c.hasUsers()){
                Log.v("warning", c.getName() + ":" + c.getID() + " has no users");
            }
        }
        for(Conversation c : conversations){
            currentConversations.put(c.getID(), c);
            addToNavBar(PRIVATE_MENU_GROUP, c);
        }

        moveOnlineConversationsUp(conversations, PRIVATE_MENU_GROUP);
    }

    public void populateUnread(List<Conversation> conversations){
        for(Conversation c : conversations){
            if( ! c.hasUsers()){
                Log.v("warning", c.getName() + ":" + c.getID() + " has no users");
            }
        }
        for(Conversation conversation : conversations){
            currentConversations.put(conversation.getID(), conversation);
            addToNavBar(UNREAD_MENU_GROUP, conversation);
        }

        moveOnlineConversationsUp(conversations, UNREAD_MENU_GROUP);
    }

    private void moveOnlineConversationsUp(List<Conversation> conversations, int groupID){
        for(Conversation c : conversations){
            if(c.hasOnlineUser()){
                navDrawerAdapter.moveConversationToFirstPosition(groupID, c);
            }
        }
    }

    private void moveAllOnlineConversationsUp(){
        moveOnlineConversationsUp(navDrawerAdapter.getOnlineInGroup(UNREAD_MENU_GROUP), UNREAD_MENU_GROUP);
        moveOnlineConversationsUp(navDrawerAdapter.getOnlineInGroup(PRIVATE_MENU_GROUP), PRIVATE_MENU_GROUP);
    }

    public void populateConversations(List<Conversation> conversations){
        List<Conversation> unreadConversations = new ArrayList<Conversation>();
        List<Conversation> privateConversations = new ArrayList<Conversation>();

        for(Conversation conversation : conversations){
            if(conversation.getNumOfUnread() > 0){
                unreadConversations.add(conversation);
            }else{
                privateConversations.add(conversation);
            }
        }

        populateUnread(unreadConversations);
        populatePrivate(privateConversations);
    }

    private void hideMainElements(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideSoftKeyboard();
                findViewById(R.id.loading_logo_image_view).setVisibility(View.VISIBLE);

                if(getSupportActionBar() != null){
                    getSupportActionBar().hide();
                }
                findViewById(R.id.main_recycler_view_holder).setVisibility(View.GONE);
                ((DrawerLayout)findViewById(R.id.drawer_layout)).setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        });

    }

    private void hideWarningBanner(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                warningBanner.setVisibility(View.GONE);
            }
        });
    }

    private void showWarningBanner(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                warningBanner.setVisibility(View.VISIBLE);
            }
        });
    }

    public void showMainElements(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.loading_logo_image_view).setVisibility(View.GONE);

                if(getSupportActionBar() != null){
                    getSupportActionBar().show();
                }
                findViewById(R.id.main_recycler_view_holder).setVisibility(View.VISIBLE);
                ((DrawerLayout)findViewById(R.id.drawer_layout)).setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }
        });

    }

    public void onLoadComplete(){
        setupFragmentAndSocket();
        if(isNewUser()){
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.openDrawer(GravityCompat.START);
            showMainElements();
        }
    }

    public  void logout(View v){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sPref.edit();
        editor.putString(LoginActivity.TOKEN, null);
        //editor.putString(LoginActivity.NAME , null);
        editor.putLong(PREV_CONVERSATION_ID , -1);
        closeSocket();
        editor.commit();

        goToLogin();
    }

    public  void viewPDF(View v){
        if(currentFragment instanceof PDFFragment){
            closeDrawer();
            return;
        }

        if(selectedConversation != null){
            selectedConversation.deselect();
            selectedConversation = null;
        }
        changeFragment();
    }

    public void updateMostRecentConversation(long conversationID){
        SharedPreferences sp = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(PREV_CONVERSATION_ID, conversationID);
        editor.commit();
    }

    public void updateUserOnline(final long userID, final boolean isOnline){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(userList.containsKey(userID)){
                    User u = userList.get(userID);
                    u.setIsOnline(isOnline);
                    updateGroup(userID, PRIVATE_MENU_GROUP);
                    updateGroup(userID, UNREAD_MENU_GROUP);
                    updateGroupStaff(userID, isOnline);
                }
                moveAllOnlineConversationsUp();
            }
        });

    }

    private Conversation findConversationWithUser(long userID, int groupID){
        for(int i = 0; i < navDrawerAdapter.getChildrenCount(getGroupPosition(groupID)); i++) {
            NavDrawerItem item = navDrawerAdapter.getChild(getGroupPosition(groupID), i);

            Conversation conversation = currentConversations.get(item.getID());
            boolean isConversation = true;
            for (int j = 0; j < conversation.getNumberOfUsers(); j++) {
                User userInConv = conversation.getUserAtIndex(j);
                if (userInConv.getID() != loggedInUser.getID() && userInConv.getID() != userID) {
                    isConversation = false;
                }
            }
            if(isConversation){
                return conversation;
            }
        }
        return null;
    }

    private void updateGroup(final long userID, final int groupID){
        if(! navDrawerAdapter.groupIsVisible(groupID)){
            return;
        }
        Conversation conversation = findConversationWithUser(userID, groupID);

        if(conversation != null){
            navDrawerAdapter.moveConversationToFirstPosition(groupID, conversation);
        }
        drawerListView.invalidateViews();

    }

    private void updateGroupStaff(final long userID, final boolean isOnline){
        drawerListView.invalidateViews();

    }

    public void moveConversationToPrivate(final Conversation conversation){
        navDrawerAdapter.moveConversationToPrivate(conversation);
        conversation.setNumOfUnread(0);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                drawerListView.invalidateViews();
            }
        });

    }

    public void moveConversationToUnread(final Conversation conversation){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                navDrawerAdapter.moveConversationToUnread(conversation);
                drawerListView.invalidateViews();
            }
        });
    }

    public void dismissNotifications(long conversationID){
        if(! isPaused){
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel((int) conversationID);
        }
    }

    public void queueToast(String s){
        queuedToastMessage = s;
    }


    /***** Methods for listening for the navigation drawer opening/closing *****/

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {
        hideSoftKeyboard();
    }

    @Override
    public void onDrawerOpened(View drawerView) {

    }

    @Override
    public void onDrawerClosed(View drawerView) {

    }

    @Override
    public void onDrawerStateChanged(int newState) {

    }

    /***************************************************/



    /********** Socket Methods **********/

    private void connectSocket(){
        connectSocket(null);
    }

    private void connectSocket(RequestWrapper.OnCompleteListener ocl){
        try{
            if(socket != null && ! socket.isClosed() && socket.isOpen()){
                Log.v("sockett", "Socket is still open. Done");
                hideWarningBanner();
                if(ocl != null){
                    ocl.onComplete("Connected");
                }
                return;
            }
            closeSocket();
            if(currentFragment == null){
                Log.v("sockett", "Error: Trying to create a socket with a null fragment");
                if(ocl != null){
                    ocl.onError("Error: Trying to create a socket with a null fragment");
                }
                return;
            }
            socket = new WebSocket(currentFragment, this);
            setupSSL(this, socket,ocl);
        }catch (URISyntaxException e){
            Log.v("sockett", "The socket failed to connect: " + e.getMessage());
            closeSocket();
            if(ocl != null){
                ocl.onError("Socket Failed to connect");
            }
        }
    }

    private void forceSocketReconnect(){
        try{
            Log.v("sockett", "Forcing reconnect");
            closeSocket();
            if(currentFragment == null){
                Log.v("sockett", "Error: Trying to create a socket with a null fragment");
                return;
            }
            socket = new WebSocket(currentFragment, this);
            setupSSL(this, socket);
        }catch (URISyntaxException e){
            Log.v("sockett", "The socket failed to forcibly connect: " + e.getMessage());
            closeSocket();
        }
    }

    private void closeSocket(){
        if(socket != null){
            Log.v("sockett", "Closing socket!");
            socket.close();
            socket = null;
        }
    }


    private void setupSSL(final Context context, final WebSocket sock){
        setupSSL(context, sock, null);
    }

    private void setupSSL(final Context context, final WebSocket sock, final RequestWrapper.OnCompleteListener ocl){

        Runnable r = new Runnable() {
            @Override
            public void run() {
                try{
                    NeuroSSLSocketFactory neuroSSLSocketFactory = new NeuroSSLSocketFactory(context);
                    org.apache.http.conn.ssl.SSLSocketFactory sslSocketFactory = neuroSSLSocketFactory.createAdditionalCertsSSLSocketFactory();
                    Socket sock1 = new Socket(RequestWrapper.BASE_URL, 443);
                    SSLSocket socketSSL = (SSLSocket) sslSocketFactory.createSocket(sock1, RequestWrapper.BASE_URL, 443, false);

                    sock.setSocket(socketSSL);
                    if(! sock.connectBlocking()){
                        if(! sock.isOpen()){
                            Log.v("sockett", "Failed to connect socket");
                            throw new Exception("Error connecting to the web socket");
                        }else{
                            hideWarningBanner();
                            if(ocl != null){
                                ocl.onComplete("Connected");
                            }
                        }
                    }else{
                        hideWarningBanner();
                        Log.v("sockett", "Connected");
                        if(ocl != null){
                            ocl.onComplete("Connected");
                        }
                    }

                }catch (Exception e){
                    Log.v("sockett", "There was a problem setting up ssl websocket");
                    e.printStackTrace();
                    if(ocl != null){
                        ocl.onError("There was a problem setting up the websocket");
                    }
                }
            }
        };

        Thread thread = new Thread(r);
        thread.start();

    }


    public void pushMessage(final String message){
        if(! (currentFragment instanceof MainFragment)){
            Log.v("taggy", "Trying to push message when current fragment is not MainFragment");
            return;
        }

        final MainFragment mainFragment = (MainFragment) currentFragment;

        if(socket == null || socket.isClosed() || ! socket.isOpen()){
            Log.v("sockett", "Socket is not in working condition while trying to send message. Reconnecting and resending message");
            connectSocket(new RequestWrapper.OnCompleteListener() {
                @Override
                public void onComplete(String s) {
                    socket.pushMessage(message);
                    mainFragment.clearMessage();
                }

                @Override
                public void onError(String s) {
                    showToast(getResources().getString(R.string.no_connection), Toast.LENGTH_LONG);
                }
            });
        }else{
            if(currentFragment == null){
                Log.v("sockett", "currentfragment is null when trying to send message");
                return;
            }
            socket.pushMessage(message);
            mainFragment.clearMessage();

        }
    }

    /***************************************************/

    private void goToLogin(){
        closeSocket();
        Intent i = new Intent(this, LoginActivity.class);
        startActivity(i);
        finish();
    }


    public void showToast( final String message,final int length){
        //TODO Make custom toast
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(! isPaused){
                    if (mostRecentToast != null && mostRecentToast.getView().isShown()){
                        mostRecentToast.cancel();
                    }
                    mostRecentToast = Toast.makeText(MainActivity.this, message, length);
                    mostRecentToast.show();
                }
            }
        });

    }

    public void toggleSettings(View view) {
        View dropdown = findViewById(R.id.settings_menu_dropdown);
        if(dropdown.getVisibility() == View.GONE){
            dropdown.setVisibility(View.VISIBLE);
        }else{
            dropdown.setVisibility(View.GONE);
        }
    }

    public void printNavDrawer(){
        navDrawerAdapter.printLists();
    }

    public int getGroupPosition(int groupID){
        return navDrawerAdapter.getGroupPosition(groupID);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //No call for super(). Bug on API Level > 11.
    }

    public void invalidateNavigationDrawer(){
        drawerListView.invalidateViews();
    }


    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        Group group = navDrawerAdapter.getGroup(groupPosition);
        group.setIsExpanded(! group.isExpanded());
        navDrawerAdapter.dataSetChanged();
        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        onViewClicked(v);
        return true;
    }

    public void onSocketDisconnected(){
        Log.v("taggy", "!! disconnected socket !!");
        if(! isPaused){
            showWarningBanner();
            //forceSocketReconnect();
        }else{
            Log.v("sockett", "activity is paused, not connecting socket");
        }
    }

    /**************************************************
     * Dev functions
     */


    public void saveBadToken(View v){
        SharedPreferences sp = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(LoginActivity.TOKEN, "This_is_a_bad_login_token");
        editor.commit();
    }


    public void logDB(View v){
        List<Conversation> conversations = messageDatabaseHelper.getConversationsList(this);
        String messageJSON = messageDatabaseHelper.getMessagesJSON(selectedConversation.getID());
        Log.v("taggy", messageJSON + "");
    }
}