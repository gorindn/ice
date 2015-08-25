/*******************************************************************************
 * Copyright (c) 2015 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation - Jordan H. Deyton, 
 *	 Robert Smith
 *******************************************************************************/
package org.eclipse.ice.client.widgets.moose;

import org.eclipse.ice.client.common.properties.TreeProperty;
import org.eclipse.ice.client.common.properties.TreePropertyCellContentProvider;
import org.eclipse.ice.datastructures.form.Entry;
import org.eclipse.jface.viewers.ICheckStateProvider;

/**
 * A class which manages the content of a cell for a MOOSETreePropertySection
 * containing a checkbox. It maintains the state of the checkbox, as well as
 * providing apprioriate tooltip information for it.
 * 
 * @author Jordan Deyton
 * @author Robert Smith
 *
 */
public class MOOSECheckStateProvider extends TreePropertyCellContentProvider
		implements ICheckStateProvider {

	/*
	 * Implements an ICheckStateProvider method
	 */
	@Override
	public boolean isChecked(Object element) {
		return (Boolean) getRowEnabled(element);
	}

	/*
	 * Implements an ICheckStateProvider method
	 */
	@Override
	public boolean isGrayed(Object element) {
		return isValid(element)
				&& !((TreeProperty) element).getEntry().isRequired();
	}

	/**
	 * Override the default tool tip to provide information about being enabled
	 * or disabled.
	 */
	@Override
	public String getToolTipText(Object element) {
		String tooltip;

		if ((Boolean) getRowEnabled(element)) {
			tooltip = "This parameter is enabled.\n"
					+ "It will be included in the input file.";
		} else {
			tooltip = "This parameter is disabled.\n"
					+ "It will be commented out in the input file.";
		}

		return tooltip;
	}

	/**
	 * Implements a method from TreePropertyCellContentProvider
	 */
	@Override
	public Object getValue(Object element) {
			return "";
	}
	
	/**
	 * Returns true if the element is valid and the {@link TreeProperty}'s
	 * {@link Entry}'s tag is not "false" (case ignored), false otherwise.
	 */
	public boolean getRowEnabled(Object element){
		// First, check that the element is valid.
		boolean isSelected = isValid(element);

		// If the element is valid, we should mark the flag as false only if the
		// tag is some variation of "false" (case ignored).
		if (isSelected) {
			String tag = ((TreeProperty) element).getEntry().getTag();
			isSelected = tag == null
					|| !"false".equals(tag.trim().toLowerCase());
		}

		return isSelected;
	}

}