//
// $Id$

package com.threerings.msoy.server;

import java.util.List;

import java.util.logging.Level;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.RepositoryUnit;
import com.samskivert.jdbc.RepositoryListenerUnit;

import com.samskivert.util.Interval;
import com.samskivert.util.ResultListener;


import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.server.InvocationException;

import com.threerings.crowd.server.PlaceManager;

import com.threerings.whirled.server.SceneManager;

import com.threerings.msoy.data.UserAction;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.SceneBookmarkEntry;
import com.threerings.msoy.item.data.all.Avatar;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.data.all.FriendEntry;
import com.threerings.msoy.web.data.FriendInviteObject;
import com.threerings.msoy.data.all.MemberName;

import com.threerings.msoy.world.data.MsoyScene;
import com.threerings.msoy.world.data.MsoySceneModel;

import com.threerings.msoy.server.persist.GroupRecord;
import com.threerings.msoy.server.persist.GroupRepository;
import com.threerings.msoy.server.persist.MemberFlowRecord;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.server.persist.MemberRepository;

import static com.threerings.msoy.Log.log;

/**
 * Manage msoy members.
 */
public class MemberManager
    implements MemberProvider
{
    /** Cache popular place computations for five seconds while we're debugging. */
    public static final long POPULAR_PLACES_CACHE_LIFE = 5*1000;

    /**
     * This can be called from any thread to queue an update of the member's current flow if they
     * are online.
     */
    public static void queueFlowUpdated (final MemberFlowRecord record)
    {
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.memberMan.flowUpdated(record);
            }
        });
    }

    /**
     * Prepares our member manager for operation.
     */
    public void init (MemberRepository memberRepo, GroupRepository groupRepo)
    {
        _memberRepo = memberRepo;
        _groupRepo = groupRepo;
        MsoyServer.invmgr.registerDispatcher(new MemberDispatcher(this), MsoyCodes.BASE_GROUP);

        _ppCache = new PopularPlacesCache();
        _ppInvalidator = new Interval(MsoyServer.omgr) {
            public void expired() {
                PopularPlacesCache newCache = new PopularPlacesCache();
                synchronized(this) {
                    _ppCache = newCache;
                }
            }
        };
        _ppInvalidator.schedule(POPULAR_PLACES_CACHE_LIFE, true);
    }

    public PopularPlacesCache getPPCache ()
    {
        synchronized(this) {
            return _ppCache;
        }
    }

    /**
     * Loads the specified member's friends list.
     *
     * Note: all the friends will be marked as offline. If you desire to know their online status,
     * that should be filled in elsewhere.
     */
    public void loadFriends (final int memberId, ResultListener<List<FriendEntry>> listener)
    {
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<List<FriendEntry>>(listener) {
            public List<FriendEntry> invokePersistResult ()
                throws PersistenceException {
                return _memberRepo.getFriends(memberId);
            }
        });
    }

    /**
     * Update the user's occupant info.
     */
    public void updateOccupantInfo (MemberObject user)
    {
        PlaceManager pmgr = MsoyServer.plreg.getPlaceManager(user.location);
        if (pmgr != null) {
            pmgr.updateOccupantInfo(user.createOccupantInfo(pmgr.getPlaceObject()));
        }
    }

    /**
     * Called when a member updates their display name. If they are online, we update their {@link
     * MemberObject} and all related occupant info records.
     */
    public void displayNameChanged (MemberName name)
    {
        MemberObject user = MsoyServer.lookupMember(name.getMemberId());
        if (user != null) {
            user.setMemberName(name);
            updateOccupantInfo(user);
        }
    }

    /**
     * Called when a member's flow is updated. If they are online we update {@link
     * MemberObject#flow}.
     */
    public void flowUpdated (MemberFlowRecord record)
    {
        MemberObject user = MsoyServer.lookupMember(record.memberId);
        if (user == null) {
            return;
        }

        user.startTransaction();
        try {
            user.setFlow(record.flow);
            if (record.accFlow != user.accFlow) {
                user.setAccFlow(record.accFlow);
            }
        } finally {
            user.commitTransaction();
        }
    }

    /**
     * Export alterFriend() functionality according to the web servlet way of doing things. 
     */
    public void alterFriend (int userId, int friendId, boolean add,
                             ResultListener<Void> listener)
    {
        alterFriend(MsoyServer.lookupMember(userId), userId, friendId, add, listener);
    }

    /**
     * Fetch the home ID for a member and return it.
     */
    public void getHomeId (final byte ownerType, final int ownerId,
                           ResultListener<Integer> listener)
    {
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<Integer>(listener) {
            public Integer invokePersistResult () throws PersistenceException {
                switch (ownerType) {
                case MsoySceneModel.OWNER_TYPE_MEMBER:
                    MemberRecord member = _memberRepo.loadMember(ownerId);
                    return (member == null) ? null : member.homeSceneId;

                case MsoySceneModel.OWNER_TYPE_GROUP:
                    GroupRecord group = _groupRepo.loadGroup(ownerId);
                    return (group == null) ? null : group.homeSceneId;

                default:
                    log.warning("Unknown ownerType provided to getHomeId " +
                        "[ownerType=" + ownerType +
                        ", ownerId=" + ownerId + "].");
                    return null;
                }
            }
            public void handleSuccess () {
                if (_result == null) {
                    handleFailure(new InvocationException("m.no_such_user"));
                } else {
                    super.handleSuccess();
                }
            }
        });
    }

    /** 
     * Called by MsoyServer to indicate that a user has logged on.  It is used to listen on the
     * member object for changes to accumulated flow so that the member's level can be updated
     * as necessary.
     */
    public void registerMember (final MemberObject member)
    {
        checkCurrentLevel(member);
        member.addListener(new AttributeChangeListener() {
            public void attributeChanged (AttributeChangedEvent event) {
                if (MemberObject.ACC_FLOW.equals(event.getName())) {
                    checkCurrentLevel(member);
                }
            }
        });
    }

    // from interface MemberProvider
    public void alterFriend (ClientObject caller, int friendId, boolean add,
                             final InvocationService.ConfirmListener lner)
        throws InvocationException
    {
        MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);
        ResultListener<Void> rl = new ResultListener<Void>() {
            public void requestCompleted (Void result) {
                lner.requestProcessed();
            }
            public void requestFailed (Exception cause) {
                lner.requestFailed(cause.getMessage());
            }
        };
        if (add) {
            MsoyServer.mailMan.deliverMessage(
                user.memberName.getMemberId(), friendId, "Be My Friend",
                null, new FriendInviteObject(), rl);

        } else {
            alterFriend(user, user.getMemberId(), friendId, add, rl);
        }
    }

    // from interface MemberProvider
    public void getHomeId (ClientObject caller, byte ownerType, int ownerId,
                          final InvocationService.ResultListener listener)
        throws InvocationException
    {
        ResultListener<Integer> rl = new ResultListener<Integer>() {
            public void requestCompleted (Integer result) {
                listener.requestProcessed(result);
            }
            public void requestFailed (Exception cause) {
                listener.requestFailed(cause.getMessage());
            }
        };
        getHomeId(ownerType, ownerId, rl);
    }

    // from interface MemberProvider
    public void setAvatar (
        ClientObject caller, int avatarItemId, final float newScale,
        final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);

        if (avatarItemId == 0) {
            // a request to return to the default avatar
            finishSetAvatar(user, null, newScale, listener);
            return;
        }

        // otherwise, make sure it exists and we own it
        MsoyServer.itemMan.getItem(
            new ItemIdent(Item.AVATAR, avatarItemId), new ResultListener<Item>() {
            public void requestCompleted (Item item) {
                Avatar avatar = (Avatar) item;
                // ensure that they own it!
                if (user.getMemberId() != avatar.ownerId) {
                    requestFailed(new Exception("An avatar that the user " +
                        "does not own was specified!"));
                } else {
                    finishSetAvatar(user, avatar, newScale, listener);
                }
            }
            public void requestFailed (Exception cause) {
                log.log(Level.WARNING, "Unable to retrieve user's avatar.", cause);
                listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
            }
        });
    }

    // from interface MemberProvider
    public void setDisplayName (ClientObject caller, final String name,
                                final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);

        // TODO: verify entered string

        MsoyServer.invoker.postUnit(new RepositoryUnit("setDisplayName") {
            public void invokePersist () throws PersistenceException {
                _memberRepo.configureDisplayName(user.getMemberId(), name);
            }
            public void handleSuccess () {
                user.setMemberName(new MemberName(name, user.getMemberId()));
                updateOccupantInfo(user);
            }
            public void handleFailure (Exception pe) {
                log.warning("Unable to set display name [user=" + user.which() +
                            ", name='" + name + "', error=" + pe + "].");
                listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
            }
        });
    }

    // from interface MemberProvider
    public void purchaseRoom (ClientObject caller, final InvocationService.ResultListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);

        // figure out if they want a group or a personal room
        SceneManager sceneMan = MsoyServer.screg.getSceneManager(user.sceneId);
        MsoyScene scene = (MsoyScene) sceneMan.getScene();
        if (!scene.canEdit(user)) {
            throw new InvocationException(InvocationCodes.E_ACCESS_DENIED);
        }
        MsoySceneModel model = (MsoySceneModel) scene.getSceneModel();
        boolean isGroup = (model.ownerType == MsoySceneModel.OWNER_TYPE_GROUP);

        final byte ownerType = isGroup ? MsoySceneModel.OWNER_TYPE_GROUP
                                       : MsoySceneModel.OWNER_TYPE_MEMBER;
        final int ownerId = isGroup ? model.ownerId : user.getMemberId();
        final String roomName = isGroup ? "New 'somegroup' room"
                                        : (user.memberName + "'s new room");

        // TODO: charge some flow

        MsoyServer.invoker.postUnit(new RepositoryUnit("purchaseRoom") {
            public void invokePersist () throws PersistenceException {
                _newRoomId = MsoyServer.sceneRepo.createBlankRoom(ownerType, ownerId, roomName);
            }
            public void handleSuccess () {
                user.addToOwnedScenes(new SceneBookmarkEntry(_newRoomId, roomName, 0));
                listener.requestProcessed(_newRoomId);
            }
            public void handleFailure (Exception pe) {
                log.warning("Unable to create a new room [user=" + user.which() +
                            ", error=" + pe + ", cause=" + pe.getCause() + "].");
                listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
            }
            protected int _newRoomId;
        });
    }
    
    /**
     * Grant a member some flow, categorized and optionally metatagged with an action type and a
     * detail String. The member's {@link MemberRecord} is updated, as is the {@link
     * DailyFlowGrantedRecord}. A {@link MemberActionLogRecord} is recorded for the supplied grant
     * action. Finally, a line is written to the flow grant log.
     */
    public void grantFlow (final int memberId, final int amount,
                           final UserAction grantAction, final String details)
    {
        MsoyServer.invoker.postUnit(new RepositoryUnit("grantFlow") {
            public void invokePersist () throws PersistenceException {
                _flowRec = _memberRepo.getFlowRepository().grantFlow(
                    memberId, amount, grantAction, details);
            }
            public void handleSuccess () {
                flowUpdated(_flowRec);
            }
            public void handleFailure (Exception pe) {
                log.log(Level.WARNING, "Unable to grant flow [memberId=" + memberId +
                        ", action=" + grantAction + ", amount=" + amount +
                        ", details=" + details + "]", pe);
            }
            protected MemberFlowRecord _flowRec;
        });
    }

    /**
     * Debit a member some flow, categorized and optionally metatagged with an action type and a
     * detail String. The member's {@link MemberRecord} is updated, as is the {@link
     * DailyFlowSpentRecord}. A {@link MemberActionLogRecord} is recorded for the supplied spend
     * action. Finally, a line is written to the flow grant log.
     */
    public void spendFlow (final int memberId, final int amount,
                           final UserAction spendAction, final String details)
    {
        MsoyServer.invoker.postUnit(new RepositoryUnit("spendFlow") {
            public void invokePersist () throws PersistenceException {
                _flowRec = _memberRepo.getFlowRepository().spendFlow(
                    memberId, amount, spendAction, details);
            }
            public void handleSuccess () {
                flowUpdated(_flowRec);
            }
            public void handleFailure (Exception pe) {
                log.log(Level.WARNING, "Unable to spend flow [memberId=" + memberId +
                        ", action=" + spendAction + ", amount=" + amount +
                        ", details=" + details + "]", pe);
            }
            protected MemberFlowRecord _flowRec;
        });
    }

    /**
     * Register and log an action taken by a specific user for humanity assessment and conversion
     * analysis purposes. Some actions grant flow as a result of being taken, this method handles
     * that granting and updating the member's flow if they are online.
     */
    public void logUserAction (MemberName member, final UserAction action, final String details)
    {
        final int memberId = member.getMemberId();
        MsoyServer.invoker.postUnit(new RepositoryUnit("takeAction") {
            public void invokePersist () throws PersistenceException {
                // record that that took the action
                _flowRec = _memberRepo.getFlowRepository().logUserAction(memberId, action, details);
            }
            public void handleSuccess () {
                if (_flowRec != null) {
                    flowUpdated(_flowRec);
                }
            }
            public void handleFailure (Exception pe) {
                log.warning("Unable to note user action [memberId=" + memberId +
                            ", action=" + action + ", details=" + details + "]");
            }
            protected MemberFlowRecord _flowRec;
        });
    }

    /** 
     * Check if the member's accumulated flow level matches up with their current level, and update
     * their current level if necessary
     */
    protected void checkCurrentLevel (final MemberObject member)
    {
        // TODO
        log.info("Current accumulated flow level updated, or user just logged in [memberId=" + 
            member.memberName.getMemberId() + ", accFlow=" + member.accFlow + "]");
    }
    
    /**
     * Generic alterFriend() functionality for the two public methods above. Please note that user
     * can be null here (i.e. offline).
     */
    protected void alterFriend (final MemberObject user, final int userId, final int friendId,
                                final boolean add, ResultListener<Void> lner)
    {
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<Void>("alterFriend", lner) {
            public Void invokePersistResult () throws PersistenceException {
                if (add) {
                    _entry = _memberRepo.inviteFriend(userId, friendId);
                    if (user != null) {
                        _userName = user.memberName;
                    } else {
                        _userName = _memberRepo.loadMember(userId).getName();
                    }
                } else {
                    _memberRepo.removeFriends(userId, friendId);
                }
                return null;
            }

            public void handleSuccess () {
                FriendEntry oldEntry = user != null ? user.friends.get(friendId) : null;
                MemberName friendName = (oldEntry != null) ?
                    oldEntry.name : (_entry != null ? _entry.name : null);
                MemberObject friendObj = (friendName != null) ?
                    MsoyServer.lookupMember(friendName) : null;

                // update ourselves and the friend
                if (!add || _entry == null) {
                    // remove the friend
                    if (oldEntry != null) {
                        if (user != null) {
                            user.removeFromFriends(friendId);
                        }
                        if (friendObj != null) {
                            friendObj.removeFromFriends(userId);
                        }
                    }

                } else {
                    // add or update the friend/status
                    _entry.online = (friendObj != null);
                    if (oldEntry == null) {
                        if (user != null) {
                            user.addToFriends(_entry);
                        }
                        if (friendObj != null) {
                            FriendEntry opp = new FriendEntry(_userName, user != null);
                            friendObj.addToFriends(opp);
                        }
                    }
                }
                _listener.requestCompleted(null);
            }

            protected FriendEntry _entry;
            protected MemberName _userName;
        });
    }

    /**
     * Convenience method to ensure that the specified caller is not a guest.
     */
    protected void ensureNotGuest (MemberObject caller)
        throws InvocationException
    {
        if (caller.isGuest()) {
            throw new InvocationException(InvocationCodes.ACCESS_DENIED);
        }
    }

    /**
     * Finish configuring the user's avatar.
     *
     * @param avatar may be null to revert to the default member avatar.
     */
    protected void finishSetAvatar (
        final MemberObject user, final Avatar avatar, final float newScale,
        final InvocationService.InvocationListener listener)
    {
        MsoyServer.invoker.postUnit(new RepositoryUnit("setAvatarPt2") {
            public void invokePersist () throws PersistenceException {
                _memberRepo.configureAvatarId(user.getMemberId(),
                    (avatar == null) ? 0 : avatar.itemId);
                if (newScale != 0 && avatar != null && avatar.scale != newScale) {
                    MsoyServer.itemMan.getAvatarRepository().updateScale(avatar.itemId, newScale);
                }
            }

            public void handleSuccess () {
                if (newScale != 0 && avatar != null) {
                    avatar.scale = newScale;
                    MsoyServer.itemMan.updateUserCache(avatar);
                }
                MsoyServer.itemMan.updateItemUsage(
                    user.getMemberId(), user.avatar, avatar, new ResultListener.NOOP<Object>() {
                    public void requestFailed (Exception cause) {
                        log.warning("Unable to update usage from an avatar change.");
                    }
                });
                user.setAvatar(avatar);
                user.avatarState = null; // clear out the state
                updateOccupantInfo(user);
            }

            public void handleFailure (Exception pe) {
                log.warning("Unable to set avatar [user=" + user.which() +
                            ", avatar='" + avatar + "', " + "error=" + pe + "].");
                log.log(Level.WARNING, "", pe);
                listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
            }
        });

    }

    /** An interval that updates the popular places cache reference every so often. */
    protected Interval _ppInvalidator;

    /** The most recent summary of popular places in the whirled. */
    protected PopularPlacesCache _ppCache;

    /** Provides access to persistent member data. */
    protected MemberRepository _memberRepo;
    
    /** Provides access to persistent group data. */
    protected GroupRepository _groupRepo;
}
