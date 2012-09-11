/*******************************************************************************
 * Copyright (c) 2005, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.loader.buddy;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import org.eclipse.osgi.container.ModuleCapability;
import org.eclipse.osgi.container.ModuleWiring;
import org.eclipse.osgi.container.namespaces.EquinoxModuleDataNamespace;
import org.eclipse.osgi.internal.loader.BundleLoader;

/**
 *Registered policy is an implementation of a buddy policy. 
 * It is responsible for looking up a class in the bundles (registrant) that declare interest in the bundle that require the buddy loading.
 * Note that the registrants must have a direct dependency on the bundle needing buddy.
 */
public class RegisteredPolicy extends DependentPolicy {

	public RegisteredPolicy(BundleLoader requester) {
		super(requester);

		//Filter the dependents;
		if (allDependents == null)
			return;

		String requesterName = requester.getWiring().getRevision().getSymbolicName();
		for (Iterator<ModuleWiring> iter = allDependents.iterator(); iter.hasNext();) {
			ModuleWiring wiring = iter.next();
			List<ModuleCapability> moduleDatas = wiring.getRevision().getModuleCapabilities(EquinoxModuleDataNamespace.MODULE_DATA_NAMESPACE);
			@SuppressWarnings("unchecked")
			List<String> registeredList = (List<String>) (moduleDatas.isEmpty() ? null : moduleDatas.get(0).getAttributes().get(EquinoxModuleDataNamespace.CAPABILITY_BUDDY_REGISTERED));
			if (registeredList == null || registeredList.isEmpty()) {
				iter.remove();
			} else {
				boolean contributes = false;
				for (String registeredName : registeredList) {
					if (registeredName.equals(requesterName)) {
						contributes = true;
						break;
					}
				}
				if (!contributes) {
					iter.remove();
				}
			}
		}

		//After the filtering, if nothing is left then null out the variable for optimization
		if (allDependents.size() == 0)
			allDependents = null;
	}

	public Class<?> loadClass(String name) {
		if (allDependents == null)
			return null;

		Class<?> result = null;
		int size = allDependents.size();
		for (int i = 0; i < size && result == null; i++) {
			ModuleWiring searchWiring = allDependents.get(i);
			BundleLoader searchLoader = (BundleLoader) searchWiring.getModuleLoader();
			try {
				result = searchLoader.findClass(name);
			} catch (ClassNotFoundException e) {
				//Nothing to do, just keep looking
				continue;
			}
		}
		return result;
	}

	public URL loadResource(String name) {
		if (allDependents == null)
			return null;

		URL result = null;
		int size = allDependents.size();
		for (int i = 0; i < size && result == null; i++) {
			ModuleWiring searchWiring = allDependents.get(i);
			BundleLoader searchLoader = (BundleLoader) searchWiring.getModuleLoader();
			result = searchLoader.findResource(name);
		}
		return result;
	}

	public Enumeration<URL> loadResources(String name) {
		if (allDependents == null)
			return null;

		Enumeration<URL> results = null;
		int size = allDependents.size();
		for (int i = 0; i < size; i++) {
			try {
				ModuleWiring searchWiring = allDependents.get(i);
				BundleLoader searchLoader = (BundleLoader) searchWiring.getModuleLoader();
				results = BundleLoader.compoundEnumerations(results, searchLoader.findResources(name));
			} catch (IOException e) {
				//Ignore and keep looking
			}
		}
		return results;
	}
}