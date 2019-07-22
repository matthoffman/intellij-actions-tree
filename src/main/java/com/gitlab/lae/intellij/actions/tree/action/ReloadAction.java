package com.gitlab.lae.intellij.actions.tree.action;

import com.gitlab.lae.intellij.actions.tree.app.AppComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;

public final class ReloadAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(AnActionEvent e) {
        ApplicationManager
                .getApplication()
                .getComponent(AppComponent.class)
                .reload();
    }

}