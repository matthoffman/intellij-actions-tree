package com.gitlab.lae.intellij.actions.tree;

import com.gitlab.lae.intellij.actions.tree.ui.ActionList;
import com.gitlab.lae.intellij.actions.tree.ui.ActionPopupEventDispatcher;
import com.gitlab.lae.intellij.actions.tree.ui.ActionPresentation;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdePopupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

import static com.gitlab.lae.intellij.actions.tree.ActionNode.ACTION_PLACE;
import static com.gitlab.lae.intellij.actions.tree.util.JBPopups.setBestLocation;
import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ESCAPE;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;
import static com.intellij.openapi.actionSystem.ex.ActionUtil.lastUpdateAndCheckDumb;
import static com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAware;
import static com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.NUMBERING;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class Popup {

    private final ActionManager actionManager;
    private final Component sourceComponent;
    private final Editor sourceEditor;
    private final IdeFocusManager focusManager;
    private final IdePopupManager popupManager;
    private final JBPopupFactory popupFactory;
    private final DataManager dataManager;
    private final ActionList list;
    private final JBPopup popup;

    Popup(
            ActionNode action,
            AnActionEvent e,
            IdeFocusManager focusManager,
            IdePopupManager popupManager,
            JBPopupFactory popupFactory,
            DataManager dataManager
    ) {
        this.focusManager = requireNonNull(focusManager);
        this.popupManager = requireNonNull(popupManager);
        this.popupFactory = requireNonNull(popupFactory);
        this.dataManager = requireNonNull(dataManager);
        this.actionManager = e.getActionManager();
        this.sourceComponent = e.getData(CONTEXT_COMPONENT);
        this.sourceEditor = e.getData(EDITOR);

        List<ActionPresentation> items = action.prepare(e.getDataContext())
                .stream()
                .map(pair -> {
                    List<KeyStroke> keys = pair.getFirst();
                    ActionNode item = pair.getSecond();
                    return createPresentation(
                            item,
                            e.getActionManager(),
                            e.getDataContext(),
                            keys
                    );
                })
                .collect(toList());

        list = new ActionList(items);

        // Register our action first before IntelliJ registers the default
        // actions (e.g. com.intellij.ui.ScrollingUtil) so that in case of
        // conflict our action will be executed
        items.forEach(item -> item.registerShortcuts(
                list,
                this::onActionChosen
        ));

        popup = createPopup();
        popup.addListener(new ActionPopupEventDispatcher(
                popup,
                list,
                popupManager
        ));

        registerIdeAction(ACTION_EDITOR_ESCAPE, popup::cancel);
    }

    void show(DataContext dataContext) {
        popup.showInBestPositionFor(dataContext);
    }

    private ActionPresentation createPresentation(
            ActionNode action,
            ActionManager actionManager,
            DataContext dataContext,
            List<KeyStroke> keysOverride
    ) {
        return action.createPresentation(
                actionManager,
                dataContext,
                focusManager,
                popupManager,
                popupFactory,
                dataManager,
                keysOverride
        );
    }

    private JBPopup createPopup() {
        return popupFactory
                .createListPopupBuilder(list)
                .setModalContext(true)
                .setCloseOnEnter(false)
                .setItemChoosenCallback(() -> {
                    ActionPresentation item = list.getSelectedValue();
                    if (item != null) {
                        onActionChosen(item, 0);
                    }
                })
                .createPopup();
    }

    private void registerIdeAction(
            String actionId,
            Runnable runnable
    ) {
        AnAction action = actionManager.getAction(actionId);
        if (action == null) {
            return;
        }
        ShortcutSet shortcutSet = action.getShortcutSet();
        if (shortcutSet.getShortcuts().length == 0) {
            return;
        }
        new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
                runnable.run();
            }
        }.registerCustomShortcutSet(shortcutSet, list);
    }

    private void onActionChosen(ActionPresentation item, ActionEvent e) {
        onActionChosen(item, e.getModifiers());
    }

    private void onActionChosen(ActionPresentation item, int modifiers) {
        if (!item.presentation().isEnabled()) {
            return;
        }

        list.setSelectedValue(item, false);

        Runnable invocation = () -> performAction(item.action(), modifiers);

        if (item.sticky()) {
            invocation.run();
            updatePopupLocation();
            updatePresentations();
        } else {
            popup.setFinalRunnable(invocation);
            popup.closeOk(null);
        }
    }

    private void updatePopupLocation() {
        if (sourceEditor != null) {
            setBestLocation(popup, popupFactory, sourceEditor);
        } else if (sourceComponent instanceof JComponent) {
            setBestLocation(
                    popup,
                    popupFactory,
                    (JComponent) sourceComponent
            );
        }
    }

    /**
     * Updates list items to show correct information regarding current
     * state, such as whether an action should be enabled/disabled for
     * current cursor position.
     */
    private void updatePresentations() {
        getDataContextAsync(dataContext -> {
            ListModel<ActionPresentation> model = list.getModel();
            for (int i = 0, l = model.getSize(); i < l; i++) {
                model.getElementAt(i).update(actionManager, dataContext);
            }
        });
    }

    private void getDataContextAsync(Consumer<DataContext> consumer) {
        (sourceComponent == null
                ? dataManager.getDataContextFromFocus()
                : AsyncResult.done(dataManager.getDataContext(sourceComponent))
        ).doWhenDone(consumer);
    }

    private void performAction(AnAction action, int modifiers) {
        /*
         * Wrapping with SwingUtilities.invokeLater()
         * then IdeFocusManager.doWhenFocusSettlesDown()
         * is required to get back to the pre-popup focus state
         * before executing the action, as some action won't
         * get executed correctly if focus is not restored, such
         * as 'Goto next Splitter'.
         */
        getDataContextAsync(dataContext ->
                SwingUtilities.invokeLater(() ->
                        focusManager.doWhenFocusSettlesDown(() ->
                                performAction(
                                        action,
                                        modifiers,
                                        dataContext
                                ))));
    }

    private void performAction(
            AnAction action,
            int modifiers,
            DataContext dataContext
    ) {
        AnActionEvent event = new AnActionEvent(
                null,
                dataContext,
                ACTION_PLACE,
                action.getTemplatePresentation().clone(),
                actionManager,
                modifiers
        );
        event.setInjectedContext(action.isInInjectedContext());

        if (showPopupIfGroup(action, event)) {
            return;
        }
        if (lastUpdateAndCheckDumb(action, event, false)) {
            performActionDumbAware(action, event);
        }
    }

    private boolean showPopupIfGroup(AnAction action, AnActionEvent e) {
        if (!(action instanceof ActionGroup)) {
            return false;
        }
        ActionGroup group = (ActionGroup) action;
        if (group.canBePerformed(e.getDataContext())) {
            return false;
        }
        popupFactory.createActionGroupPopup(
                e.getPresentation().getText(),
                group,
                e.getDataContext(),
                NUMBERING,
                true
        ).showInBestPositionFor(e.getDataContext());
        return true;
    }
}
