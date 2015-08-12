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

package com.github.oneminusone.disableantipiracy;



import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.app.Service;

import static de.robv.android.xposed.XposedHelpers.*;

public class DisableAntiPiracy implements IXposedHookLoadPackage {

    private boolean isAntiPiracy(String clazz) {
        return (clazz.equals("org.antipiracy.support.AntiPiracyInstallReceiver") ||
                clazz.equals("org.antipiracy.support.AntiPiracyNotifyService") ||
                clazz.equals("org.antipiracy.support.ContentGuardInstallReceiver") ||
                clazz.equals("org.antipiracy.support.ContentGuardNotifyService"));
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // Intercept services
        if (lpparam.packageName.equals("com.android.settings")) {
            Class installReceiver, notifyService;
            try {
                installReceiver = findClass("org.antipiracy.support.ContentGuardInstallReceiver", lpparam.classLoader);
                notifyService = findClass("org.antipiracy.support.ContentGuardNotifyService", lpparam.classLoader);
            } catch (ClassNotFoundError e1) {
                try {
                    installReceiver = findClass("org.antipiracy.support.AntiPiracyInstallReceiver", lpparam.classLoader);
                    notifyService = findClass("org.antipiracy.support.AntiPiracyNotifyService", lpparam.classLoader);
                } catch (ClassNotFoundError e2) {
                    XposedBridge.log("AntiPiracy not found");
                    return;
                }
            }
            findAndHookMethod(installReceiver, "onReceive", Context.class, Intent.class, XC_MethodReplacement.returnConstant(null));
            findAndHookMethod(notifyService, "onStartCommand", Intent.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    callMethod(param.thisObject, "shutdown");
                    param.setResult(Service.START_NOT_STICKY);
                }
            });
        }

        if (lpparam.packageName.equals("android")) {
            // Enable disabling services
            findAndHookMethod("com.android.server.pm.PackageManagerService", lpparam.classLoader,
                    "setComponentEnabledSetting", ComponentName.class, int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            ComponentName componentName = (ComponentName) param.args[0];
                            if (isAntiPiracy(componentName.getClassName())) {
                                Object sUserManager = getObjectField(param.thisObject, "sUserManager");
                                if ((boolean) callMethod(sUserManager, "exists", param.args[3])) {
                                    callMethod(param.thisObject, "setEnabledSetting", componentName.getPackageName(),
                                            componentName.getClassName(), param.args[1], param.args[2], param.args[3], null);
                                    param.setResult(null);
                                }
                            }
                        }
                    });

            // Disable enabling services
            findAndHookMethod("com.android.server.pm.PackageSettingBase", lpparam.classLoader,
                    "enableComponentLPw", String.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String componentClassName = (String) param.args[0];
                            if (isAntiPiracy(componentClassName)) {
                                param.setResult(false);
                            }
                        }
                    });
        }
    }
}
