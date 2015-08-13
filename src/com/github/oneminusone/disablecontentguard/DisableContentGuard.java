/*
 * Copyright (C) 2015
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.oneminusone.disablecontentguard;



import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;

import static de.robv.android.xposed.XposedHelpers.*;

public class DisableContentGuard implements IXposedHookLoadPackage {

    private static boolean debug = false;

    private boolean isAntiPiracy(String clazz) {
        return (clazz.equals("org.antipiracy.support.AntiPiracyInstallReceiver") ||
                clazz.equals("org.antipiracy.support.AntiPiracyNotifyService") ||
                clazz.equals("org.antipiracy.support.ContentGuardInstallReceiver") ||
                clazz.equals("org.antipiracy.support.ContentGuardNotifyService"));
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        Class installReceiver, notifyService;

        /* Intercept AntiPiracy */
        if (lpparam.packageName.equals("com.android.settings")) {
            try {
                try {
                    installReceiver = findClass("org.antipiracy.support.ContentGuardInstallReceiver", lpparam.classLoader);
                    notifyService = findClass("org.antipiracy.support.ContentGuardNotifyService", lpparam.classLoader);
                } catch (ClassNotFoundError e) {
                    installReceiver = findClass("org.antipiracy.support.AntiPiracyInstallReceiver", lpparam.classLoader);
                    notifyService = findClass("org.antipiracy.support.AntiPiracyNotifyService", lpparam.classLoader);
                }

                findAndHookMethod(installReceiver, "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(null);
                        if (debug) XposedBridge.log("Intercepted InstallReceiver");
                    }
                });
                findAndHookMethod(notifyService, "onStartCommand", Intent.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        callMethod(param.thisObject, "shutdown");
                        param.setResult(android.app.Service.START_NOT_STICKY);
                        if (debug) XposedBridge.log("Intercepted NotifyService");
                    }
                });
            } catch (ClassNotFoundError e) {
                if (debug) XposedBridge.log("AntiPiracy not found");
            }
        }

        /* Intercept ContentGuard */
        if (lpparam.packageName.equals("com.android.systemui")) {
            try {
                findAndHookMethod("com.android.internal.util.exodus.DeviceUtils", lpparam.classLoader,
                        "parsePackageUri", Context.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(false);
                                if (debug) XposedBridge.log("Intercepted ContentGuard");
                            }
                        });
            } catch (ClassNotFoundError e) {
                if (debug) XposedBridge.log("ContentGuard not found");
            }
        }

        /* Revert changes to android framework */
        if (lpparam.packageName.equals("android")) {
            findAndHookMethod("com.android.server.pm.PackageManagerService", lpparam.classLoader,
                    "setComponentEnabledSetting", ComponentName.class, int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            ComponentName componentName = (ComponentName) param.args[0];
                            int newState = (int) param.args[1];
                            if (componentName.getClassName().equals("com.android.defcontainer.DefaultContainerService")
                                    && newState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                                // Disable disabling application installs
                                param.setResult(null);
                                if (debug) XposedBridge.log("Keep DefaultContainerService enabled");
                            } else if (isAntiPiracy(componentName.getClassName())) {
                                // Enable disabling AntiPiracy services
                                Object sUserManager = getObjectField(param.thisObject, "sUserManager");
                                if ((boolean) callMethod(sUserManager, "exists", param.args[3])) {
                                    callMethod(param.thisObject, "setEnabledSetting", componentName.getPackageName(),
                                            componentName.getClassName(), param.args[1], param.args[2], param.args[3], null);
                                    param.setResult(null);
                                    if (debug) XposedBridge.log("Enable setEnabledSetting of " + componentName.getClassName());
                                }
                            }
                        }
                    });

            // Disable AntiPiracy services
            findAndHookMethod("com.android.server.pm.PackageSettingBase", lpparam.classLoader,
                    "enableComponentLPw", String.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String componentClassName = (String) param.args[0];
                            if (isAntiPiracy(componentClassName)) {
                                boolean result = (boolean) callMethod(param.thisObject, "disableComponentLPw", param.args[0], param.args[1]);
                                param.setResult(result);
                                if (debug) XposedBridge.log("Disabled " + componentClassName);
                            }
                        }
                    });
        }
    }
}
