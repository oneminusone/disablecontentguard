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



import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import de.robv.android.xposed.XC_MethodReplacement;

import android.content.Context;
import android.content.Intent;
import android.app.Service;

public class DisableAntiPiracy implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.settings")) {
            findAndHookMethod("org.antipiracy.support.AntiPiracyInstallReceiver", lpparam.classLoader,
                    "onReceive", Context.class, Intent.class, XC_MethodReplacement.returnConstant(null));
            findAndHookMethod("org.antipiracy.support.AntiPiracyNotifyService", lpparam.classLoader,
                    "onStartCommand", Intent.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            callMethod(param.thisObject, "shutdown");
                            param.setResult(Service.START_NOT_STICKY);
                        }
                    });
        }
    }
}
