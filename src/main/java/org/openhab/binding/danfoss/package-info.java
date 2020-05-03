/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
@org.osgi.annotation.bundle.Header(name = org.osgi.framework.Constants.BUNDLE_NATIVECODE, value = "jni/windows_amd64/opensdg_jni.dll;processor=amd64;osname=win32,jni/linux_arm64/libopensdg_jni.so;processor=aarch64;osname=linux,jni/linux_armhf/libopensdg_jni.so;processor=arm;osname=linux,jni/linux_amd64/libopensdg_jni.so;processor=amd64;osname=linux,jni/linux_i386/libopensdg_jni.so;processor=x86;osname=linux,*")
package org.openhab.binding.danfoss;
