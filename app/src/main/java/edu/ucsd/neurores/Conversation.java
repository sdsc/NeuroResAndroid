package edu.ucsd.neurores;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tbpetersen on 3/23/2017.
 */

public class Conversation extends NavDrawerItem{
    private List<User> users;
    private int numOfUnseen;

    Conversation(long id, Context c){
        super(id, c);
        users = new ArrayList<User>();
    }

    void addUser(User user) {
        users.add(user);
    }

    public String getName() {
        if(users == null || users.size() == 0){
            return "Bugged out conversation";
        }

        if(users.size() == 1){
            return users.get(0).getName();
        }

        StringBuilder sb = new StringBuilder();
        for(User u: users){
            sb.append(u.getName());
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

    public void setContext(MainActivity activity) {
        super.setContext(activity);
    }

    public String getUser(long from) {
        for(User u: users){
            if(u.getID().equals(from))
                return u.getName();
        }
        return null;
    }

    public User getUserAtIndex(int index){
        return users.get(index);
    }

    public void logAllNames(){
        for(User u : users){
            Log.v("tag", "Name: " + u.getName());
        }
    }

    public int getNumOfUnseen(){
        return numOfUnseen;
    }

    public void setNumOfUnseen(int numOfUnseen){
        this.numOfUnseen = numOfUnseen;
    }

    public List<Long> getUserIDs(){
        List<Long> userIDs = new ArrayList<Long>();
        for(User u : users){
            userIDs.add(u.getID());
        }
        return userIDs;
    }

    public boolean hasOnlineUser(){
        boolean hasOnlineUser = false;
        for(User u :users){
            if(u.isOnline()){
                hasOnlineUser = true;
            }
        }
        return hasOnlineUser;
    }

    public String toString(){
        return getName() + ": " + getID();
    }

    public boolean hasUsers(){
        return users.size() > 0;
    }

    public int getNumberOfUsers(){
        return users.size();
    }
}
