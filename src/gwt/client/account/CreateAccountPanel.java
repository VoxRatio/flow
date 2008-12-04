//
// $Id$

package client.account;

import java.util.Date;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListener;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.SourcesFocusEvents;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.FloatPanel;
import com.threerings.gwt.ui.SmartTable;
import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.web.gwt.AccountInfo;
import com.threerings.msoy.web.gwt.CaptchaException;
import com.threerings.msoy.web.gwt.Invitation;
import com.threerings.msoy.web.gwt.Pages;
import com.threerings.msoy.web.gwt.RegisterInfo;
import com.threerings.msoy.web.gwt.SessionData;
import com.threerings.msoy.web.gwt.WebMemberService;
import com.threerings.msoy.web.gwt.WebMemberServiceAsync;
import com.threerings.msoy.web.gwt.WebUserService;
import com.threerings.msoy.web.gwt.WebUserServiceAsync;

import client.shell.CShell;
import client.shell.EntryVectorCookie;
import client.shell.ShellMessages;
import client.ui.DateFields;
import client.ui.MsoyUI;
import client.util.ClickCallback;
import client.util.DateUtil;
import client.util.Link;
import client.util.NoopAsyncCallback;
import client.util.ServiceUtil;

/**
 * Displays an interface for creating a new account.
 */
public class CreateAccountPanel extends FlowPanel
{
    public CreateAccountPanel ()
    {
        setStyleName("createAccount");

        add(WidgetUtil.makeShim(15, 15));
        FlowPanel content = MsoyUI.createFlowPanel("Content");
        add(content);

        // Logon button floats on the right
        FloatPanel logonLink = new FloatPanel("Logon");
        content.add(logonLink);
        logonLink.add(MsoyUI.createLabel(_msgs.createAlreadyMember(), "AlreadyMember"));
        PushButton logonButton = MsoyUI.createButton(
            MsoyUI.SHORT_THIN, _msgs.createLogonLink(),
            Link.createListener(Pages.ACCOUNT, "logon"));
        MsoyUI.addTrackingListener(logonButton, "signupLogonButtonClicked", null);
        logonLink.add(logonButton);

        content.add(MsoyUI.createLabel(_msgs.createIntro(), "Intro"));
        content.add(MsoyUI.createLabel(_msgs.createCoins(), "Coins"));

        // A/B (/C/D) test banner floats on the right and promotes some part of whirled
        content.add(_promoPanel = MsoyUI.createSimplePanel(null, "Promo"));

        content.add(new LabeledBox(_msgs.createEmail(),
                           _email = MsoyUI.createTextBox("", MemberName.MAX_EMAIL_LENGTH, -1),
                           _msgs.createEmailTip()));
        _email.addKeyboardListener(_onType);
        Invitation invite = CShell.frame.getActiveInvitation();
        if (invite != null && invite.inviteeEmail.matches(MsoyUI.EMAIL_REGEX)) {
            // provide the invitation email as the default
            _email.setText(invite.inviteeEmail);
        }

        content.add(new LabeledBox(_msgs.createRealName(), _rname = new TextBox(),
                           _msgs.createRealNameTip()));
        _rname.addKeyboardListener(_onType);

        content.add(new LabeledBox(_msgs.createDateOfBirth(), _dateOfBirth = new DateFields(),
                           _msgs.createDateOfBirthTip()));

        content.add(new LabeledBox(_msgs.createPassword(), _password = new PasswordTextBox(),
                           _msgs.createPasswordTip()));
        _password.addKeyboardListener(_onType);

        content.add(new LabeledBox(_msgs.createConfirm(), _confirm = new PasswordTextBox(),
                           _msgs.createConfirmTip()));
        _confirm.addKeyboardListener(_onType);

        _name = MsoyUI.createTextBox("", MemberName.MAX_DISPLAY_NAME_LENGTH, -1);
        _name.addKeyboardListener(_onType);
        content.add(new LabeledBox(_msgs.createDisplayName(), _name, _msgs.createDisplayNameTip()));

        // optionally add the recaptcha component
        if (RecaptchaUtil.isEnabled()) {
            content.add(new LabeledBox(_msgs.createCaptcha(),
                               new HTML("<div id=\"recaptchaDiv\"></div>"), null));
            content.add(new HTML("<div id=\"recaptchaDiv\"></div>"));
        }

        content.add(new LabeledBox("", _tosBox = new CheckBox(_msgs.createTOSAgree(), true), null));

        HorizontalPanel controls = new HorizontalPanel();
        controls.setWidth("475px");

        controls.add(_status = MsoyUI.createSimplePanel(null, "Status"));
        controls.add(WidgetUtil.makeShim(10, 10));
        controls.setHorizontalAlignment(HasAlignment.ALIGN_RIGHT);

        PushButton create = MsoyUI.createButton(MsoyUI.LONG_THICK, _msgs.createGo(), null);
        controls.add(create);
        content.add(controls);

        // create our click callback that handles the actual registation process
        new ClickCallback<SessionData>(create) {
            @Override protected boolean callService () {
                if (!validateData()) {
                    return false;
                }

                String[] today = new Date().toString().split(" ");
                String thirteenYearsAgo = "";
                for (int ii = 0; ii < today.length; ii++) {
                    if (today[ii].matches("[0-9]{4}")) {
                        int year = Integer.valueOf(today[ii]).intValue();
                        today[ii] = "" + (year - 13);
                    }
                    thirteenYearsAgo += today[ii] + " ";
                }

                Date dob = DateUtil.toDate(_dateOfBirth.getDate());
                if (DateUtil.newDate(thirteenYearsAgo).compareTo(dob) < 0) {
                    setStatus(_msgs.createNotThirteen());
                    return false;
                }

                RegisterInfo info = new RegisterInfo();
                info.email = _email.getText().trim();
                info.password = CShell.frame.md5hex(_password.getText().trim());
                info.displayName = _name.getText().trim();
                info.birthday = _dateOfBirth.getDate();
                info.photo = null; // TODO: remove since we're not using this any more
                info.info = new AccountInfo();
                info.info.realName = _rname.getText().trim();
                info.expireDays = 1; // TODO: unmagick?
                Invitation invite = CShell.frame.getActiveInvitation();
                info.inviteId = (invite == null) ? null : invite.inviteId;
                info.guestId = CShell.isGuest() ? CShell.getMemberId() : 0;
                info.visitor = CShell.visitor;
                info.captchaChallenge =
                    RecaptchaUtil.isEnabled() ? RecaptchaUtil.getChallenge() : null;
                info.captchaResponse =
                    RecaptchaUtil.isEnabled() ? RecaptchaUtil.getResponse() : null;

                setStatus(_msgs.creatingAccount());
                _usersvc.register(DeploymentConfig.version, info, this);
                return true;
            }

            @Override protected boolean gotResult (final SessionData result) {
                // display a nice confirmation message, as an excuse to embed a tracking iframe.
                // we'll show it for two seconds, and then rock on!
                setStatus(_msgs.creatingDone(),
                          ConversionTrackingUtil.createAdWordsTracker(),
                          ConversionTrackingUtil.createBeacon(EntryVectorCookie.get()));


                // temporary - trying to debug Google Analytics discrepancy
                if (_pageViewRecorded) {
                    _membersvc.trackClientAction(CShell.visitor, "registration page - joined", null,
                                                 new NoopAsyncCallback());
                }

                new FinishRegistration(result);

                return false; // don't reenable the create button
            }

            @Override protected void reportFailure (Throwable cause) {
                if (RecaptchaUtil.isEnabled()) {
                    RecaptchaUtil.reload();
                    if (cause instanceof CaptchaException) {
                        RecaptchaUtil.focus();
                    }
                }
                setStatus(CShell.serverError(cause));
            }
        };

        // temporary - trying to debug Google Analytics discrepancy
        _membersvc.trackClientAction(CShell.visitor, "registration page - viewed", null,
            new AsyncCallback<Void>() {
                public void onFailure (Throwable caught) {}
                public void onSuccess (Void result) {
                    _pageViewRecorded = true;
                }
            });


        // A/B test different header images on this page
        _membersvc.getABTestGroup(CShell.visitor, "2008 12 AccountCreationHeader", true,
            new AsyncCallback<Integer>() {
            public void onSuccess (Integer group) {
                gotABTestGroup(group);
            }

            public void onFailure (Throwable cause) {
                gotABTestGroup(-1);
            }
        });
    }

    /**
     * Display a different banner (or none) for each one of the test groups.
     */
    protected void gotABTestGroup (int groupId)
    {
        String imagePath = null;
        if (groupId == 2) {
            imagePath = "/images/account/create_banner_avatars.png";
        } else if (groupId == 3) {
            imagePath = "/images/account/create_banner_deorate.png";
        } else if (groupId == 4) {
            imagePath = "/images/account/create_banner_games.png";
        }
        // Group 1 and No Group don't see a banner.
        if (imagePath == null) {
            return;
        }
        _promoPanel.setWidget(new Image(imagePath));
    }

    @Override // from Widget
    protected void onLoad ()
    {
        super.onLoad();
        DeferredCommand.addCommand(new Command() {
            public void execute () {
                _email.setFocus(true);
            }
        });
        RecaptchaUtil.init("recaptchaDiv");
    }

    protected boolean validateData ()
    {
        String email = _email.getText().trim(), name = _name.getText().trim();
        String password = _password.getText().trim(), confirm = _confirm.getText().trim();
        String status;
        FocusWidget toFocus = null;
        if (email.length() == 0) {
            status = _msgs.createMissingEmail();
            toFocus = _email;
        } else if (password.length() == 0) {
            status = _msgs.createMissingPassword();
            toFocus = _password;
        } else if (confirm.length() == 0) {
            status = _msgs.createMissingConfirm();
            toFocus = _confirm;
        } else if (!password.equals(confirm)) {
            status = _msgs.createPasswordMismatch();
            toFocus = _confirm;
        } else if (_dateOfBirth.getDate() == null) {
            status = _msgs.createMissingDoB();
            // this is not a FocusWidget so we have to handle it specially
            _dateOfBirth.setFocus(true);
        } else if (!MemberName.isValidDisplayName(name)) {
            status = _cmsgs.displayNameInvalid("" + MemberName.MIN_DISPLAY_NAME_LENGTH,
                "" + MemberName.MAX_DISPLAY_NAME_LENGTH);
            toFocus = _name;
        } else if (!MemberName.isValidNonSupportName(name)) {
            status = _cmsgs.nonSupportNameInvalid();
            toFocus = _name;
        } else if (!_tosBox.isChecked()) {
            status = _msgs.createMustAgreeTOS();
        } else if (RecaptchaUtil.isEnabled() && (RecaptchaUtil.getResponse() == null ||
                                                 RecaptchaUtil.getResponse().length() == 0)) {
            status = _msgs.createMustCaptcha();
            RecaptchaUtil.focus();
        } else {
            return true;
        }

        if (toFocus != null) {
            toFocus.setFocus(true);
        }
        setStatus(status);
        return false;
    }

    protected void createAccount ()
    {
    }

    protected void setStatus (String text, Widget ... trackers)
    {
        FlowPanel p = new FlowPanel();
        p.add(MsoyUI.createLabel(text, ""));
        for (Widget tracker : trackers) {
            if (tracker != null) {
                p.add(tracker);
            }
        }

        _status.setWidget(p);
    }

    protected void setStatus (String text)
    {
        _status.setWidget(MsoyUI.createLabel(text, ""));
    }

    protected static Widget makeStep (int step, Widget contents)
    {
        SmartTable table = new SmartTable("Step", 0, 0);
        table.setText(0, 0, step + ".", 1, "Number");
        table.setWidget(0, 1, contents, 1, null);
        return table;
    }

    protected static class LabeledBox extends FlowPanel
    {
        public LabeledBox (String title, Widget contents, String tip)
        {
            setStyleName("Box");
            _tip = new SmartTable("Tip", 0, 0);
            add(title, contents, tip);
        }

        public void add (String title, final Widget contents, final String tip)
        {
            add(MsoyUI.createHTML(title, "Label"));
            add(contents);
            if (contents instanceof SourcesFocusEvents) {
                ((SourcesFocusEvents)contents).addFocusListener(new FocusListener() {
                    public void onFocus (Widget sender) {
                        // we want contents here not sender because of DateFields
                        showTip(contents, tip);
                    }
                    public void onLostFocus (Widget sender) {
                        if (_tip.isAttached()) {
                            remove(_tip);
                        }
                    }
                });
            }
        }

        protected void showTip (Widget trigger, String tip)
        {
            if (!_tip.isAttached() && tip != null) {
                DOM.setStyleAttribute(
                    _tip.getElement(), "left", (trigger.getOffsetWidth()+160) + "px");
                DOM.setStyleAttribute(
                    _tip.getElement(), "top", (trigger.getAbsoluteTop() - getAbsoluteTop() +
                    trigger.getOffsetHeight()/2 - 27) + "px");
                _tip.setText(0, 0, tip);
                add(_tip);
            }
        }

        protected SmartTable _tip;
    }

    /**
     * Requests the registration a/b group and after a minimum timeout lets the frame know we're
     * logged on.
     */
    protected static class FinishRegistration extends Timer
        implements AsyncCallback<Integer>
    {
        long start = System.currentTimeMillis();
        final int feedbackDelayMs = 2000;
        Integer group;
        SessionData session;

        public FinishRegistration (SessionData result)
        {
            session = result;
            // TODO: If this is useful beyond the force invite test, it would be more efficient
            // for the server to fill in the result.registrationABGroup before sending the result
            // rather than making a separate request.
            _membersvc.getABTestGroup(
                result.visitor, "2008 11 force invite on registration", true, this);
            schedule(feedbackDelayMs);
        }

        public void onFailure (Throwable caught)
        {
            group = -1;
        }

        public void onSuccess (Integer result)
        {
            group = result;
        }

        public void run ()
        {
            if (group == null) {
                // keep waiting for the member service to return
                schedule(500);
                return;
            }

            // let the top-level frame know that we logged on (will trigger a redirect)
            session.registrationABGroup = group;
            CShell.frame.dispatchDidLogon(session);
        }
    }

    protected KeyboardListenerAdapter _onType = new KeyboardListenerAdapter() {
        public void onKeyDown (Widget sender, char keyCode, int modifiers) {
            setStatus("");
        }
    };

    protected TextBox _email, _name, _rname;
    protected PasswordTextBox _password, _confirm;
    protected DateFields _dateOfBirth;
    protected CheckBox _tosBox;
    protected SimplePanel _status;
    protected boolean _pageViewRecorded;
    protected SimplePanel _promoPanel;

    protected static final ShellMessages _cmsgs = GWT.create(ShellMessages.class);
    protected static final AccountMessages _msgs = GWT.create(AccountMessages.class);
    protected static final WebUserServiceAsync _usersvc = (WebUserServiceAsync)
        ServiceUtil.bind(GWT.create(WebUserService.class), WebUserService.ENTRY_POINT);
    protected static final WebMemberServiceAsync _membersvc = (WebMemberServiceAsync)
        ServiceUtil.bind(GWT.create(WebMemberService.class), WebMemberService.ENTRY_POINT);
}
