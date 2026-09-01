/*
 EcmDroid - Android Diagnostic Tool for Buell Motorcycles
 Copyright (C) 2019 by Michel Marti

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package biz.logicminds.buelltune;

import android.app.Application;

public class EcmDroidApp extends Application {
    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer(this);
    }

    /**
     * Dependency-injection root (KTD5). Lazily built if accessed before
     * {@link #onCreate()} has run, which does not happen in practice since
     * Android always calls onCreate() before any other component - kept
     * defensive so a future test harness constructing this Application
     * directly cannot NPE on a null container.
     */
    public AppContainer getAppContainer() {
        if (appContainer == null) {
            appContainer = new AppContainer(this);
        }
        return appContainer;
    }
}
