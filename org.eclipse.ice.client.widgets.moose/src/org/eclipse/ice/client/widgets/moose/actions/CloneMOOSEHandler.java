/*******************************************************************************
 * Copyright (c) 2015 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alex McCaskey - Initial API and implementation and/or initial documentation
 *   
 *******************************************************************************/
package org.eclipse.ice.client.widgets.moose.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.CCProjectNature;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CIncludePathEntry;
import org.eclipse.cdt.core.settings.model.CSourceEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICFolderDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionManager;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.core.settings.model.ICSourceEntry;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationData;
import org.eclipse.cdt.make.core.IMakeCommonBuildInfo;
import org.eclipse.cdt.make.core.IMakeTarget;
import org.eclipse.cdt.make.core.IMakeTargetManager;
import org.eclipse.cdt.make.core.MakeCorePlugin;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.Configuration;
import org.eclipse.cdt.managedbuilder.internal.core.ManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.internal.core.ManagedProject;
import org.eclipse.cdt.managedbuilder.ui.wizards.CfgHolder;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ice.client.widgets.moose.nature.MooseNature;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CloneMOOSEHandler clones the idaholab/moose repo. Additionally, it
 * imports the project as a CDT Makefile project with existing code, creates new
 * Make Targets, and adds the appropriate MOOSE include files to the Paths and
 * Symbols preference page.
 * 
 * @authors Alex McCaskey, Dasha Gorin
 * 
 */
public class CloneMOOSEHandler extends AbstractHandler {

	/**
	 * Logger for handling event messages and other information.
	 */
	private static final Logger logger = LoggerFactory.getLogger(CloneMOOSEHandler.class);

	/**
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.commands.AbstractHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		// Create a File reference to the repo in the Eclipse workspace
		final Job job = new Job("Clone MOOSE!") {

			@SuppressWarnings("restriction")
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Attempting to clone MOOSE", 100);

				// Local Declarations
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				String os = System.getProperty("os.name");
				String sep = System.getProperty("file.separator");

				// Construct the Remote URI for the repo
				String remoteURI = "https://github.com/idaholab/moose";

				// Create the workspace file
				File workspaceFile = new File(
						ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString() + sep + "moose");

				// Clone the Repository!
				monitor.subTask("Cloning MOOSE to local machine...");
				monitor.worked(40);

				try {

					Git.cloneRepository().setURI(remoteURI).setDirectory(workspaceFile).call();

				} catch (GitAPIException e) {
					logger.error(getClass().getName() + " Exception!", e);
					String errorMessage = "ICE failed in cloning MOOSE.";
					return new Status(IStatus.ERROR, "org.eclipse.ice.client.widgets.moose", 1, errorMessage, null);
				}

				/*------------ The rest is about importing the C++ project correctly ---------*/

				// Get the project and project description handles
				IProject project = workspace.getRoot().getProject("moose");
				IProjectDescription description = workspace.newProjectDescription("moose");

				monitor.subTask("Converting application to CDT C++ Project...");
				monitor.worked(80);

				try {
					// Create the CDT Project
					CCorePlugin.getDefault().createCDTProject(description, project, new NullProgressMonitor());

					// Add the CPP nature
					CCProjectNature.addCCNature(project, new NullProgressMonitor());

					// Set up build information
					ICProjectDescriptionManager pdMgr = CoreModel.getDefault().getProjectDescriptionManager();
					ICProjectDescription projDesc = pdMgr.createProjectDescription(project, false);
					ManagedBuildInfo info = ManagedBuildManager.createBuildInfo(project);
					ManagedProject mProj = new ManagedProject(projDesc);
					info.setManagedProject(mProj);

					// Grab the correct toolchain
					// FIXME this should be better...
					IToolChain toolChain = null;
					for (IToolChain tool : ManagedBuildManager.getRealToolChains()) {
						if (os.contains("Mac") && tool.getName().contains("Mac") && tool.getName().contains("GCC")) {
							toolChain = tool;
							break;
						} else if (os.contains("Linux") && tool.getName().contains("Linux")
								&& tool.getName().contains("GCC")) {
							toolChain = tool;
							break;
						} else if (os.contains("Windows") && tool.getName().contains("Cygwin")) {
							toolChain = tool;
							break;
						} else {
							toolChain = null;
						}
					}

					// Set up the Build configuration
					CfgHolder cfgHolder = new CfgHolder(toolChain, null);
					String s = toolChain == null ? "0" : toolChain.getId(); //$NON-NLS-1$
					IConfiguration config = new Configuration(mProj,
							(org.eclipse.cdt.managedbuilder.internal.core.ToolChain) toolChain,
							ManagedBuildManager.calculateChildId(s, null), cfgHolder.getName());
					IBuilder builder = config.getEditableBuilder();
					builder.setManagedBuildOn(false);
					CConfigurationData data = config.getConfigurationData();
					projDesc.createConfiguration(ManagedBuildManager.CFG_DATA_PROVIDER_ID, data);
					pdMgr.setProjectDescription(project, projDesc);

					// Create a Make Target for the MOOSE user that builds
					// libmesh
					IProject cProject = projDesc.getProject();
					IMakeTargetManager manager = MakeCorePlugin.getDefault().getTargetManager();
					String[] ids = manager.getTargetBuilders(cProject);
					IMakeTarget libmeshTarget = manager.createTarget(cProject, "Build libmesh", ids[0]);
					libmeshTarget.setStopOnError(false);
					libmeshTarget.setRunAllBuilders(false);
					libmeshTarget.setUseDefaultBuildCmd(false);
					libmeshTarget.setBuildAttribute(IMakeCommonBuildInfo.BUILD_COMMAND, "sh scripts/update_and_rebuild_libmesh.sh");
					libmeshTarget.setBuildAttribute(IMakeTarget.BUILD_LOCATION, cProject.getLocation().toOSString());
					libmeshTarget.setBuildAttribute(IMakeTarget.BUILD_ARGUMENTS, "");
					libmeshTarget.setBuildAttribute(IMakeTarget.BUILD_TARGET, "all");
					manager.addTarget(cProject, libmeshTarget);

					// Create a Make Target for the MOOSE user that builds moose
					IMakeTarget mooseTarget = manager.createTarget(cProject, "Build MOOSE", ids[0]);
					mooseTarget.setStopOnError(false);
					mooseTarget.setRunAllBuilders(false);
					mooseTarget.setUseDefaultBuildCmd(false);
					mooseTarget.setBuildAttribute(IMakeCommonBuildInfo.BUILD_COMMAND, "make -C framework");
					mooseTarget.setBuildAttribute(IMakeTarget.BUILD_LOCATION, cProject.getLocation().toOSString());
					mooseTarget.setBuildAttribute(IMakeTarget.BUILD_ARGUMENTS, "");
					mooseTarget.setBuildAttribute(IMakeTarget.BUILD_TARGET, "all");
					manager.addTarget(cProject, mooseTarget);

					// Set the include and src folders as actual CDT source
					// folders
					ICProjectDescription cDescription = CoreModel.getDefault().getProjectDescriptionManager()
							.createProjectDescription(cProject, false);
					ICConfigurationDescription cConfigDescription = cDescription.createConfiguration(
							ManagedBuildManager.CFG_DATA_PROVIDER_ID, config.getConfigurationData());
					cDescription.setActiveConfiguration(cConfigDescription);
					cConfigDescription.setSourceEntries(null);
					IFolder srcFolder = cProject.getFolder("src");
					IFolder includeFolder = cProject.getFolder("include");
					ICSourceEntry srcFolderEntry = new CSourceEntry(srcFolder, null, ICSettingEntry.RESOLVED);
					ICSourceEntry includeFolderEntry = new CSourceEntry(includeFolder, null, ICSettingEntry.RESOLVED);
					cConfigDescription.setSourceEntries(new ICSourceEntry[] { srcFolderEntry, includeFolderEntry });

					// Add the Moose include paths
					ICProjectDescription projectDescription = CoreModel.getDefault().getProjectDescription(cProject,
							true);
					ICConfigurationDescription configDecriptions[] = projectDescription.getConfigurations();
					for (ICConfigurationDescription configDescription : configDecriptions) {
						ICFolderDescription projectRoot = configDescription.getRootFolderDescription();
						ICLanguageSetting[] settings = projectRoot.getLanguageSettings();
						for (ICLanguageSetting setting : settings) {
							List<ICLanguageSettingEntry> includes = getIncludePaths();
							includes.addAll(setting.getSettingEntriesList(ICSettingEntry.INCLUDE_PATH));
							setting.setSettingEntries(ICSettingEntry.INCLUDE_PATH, includes);
						}
					}
					CoreModel.getDefault().setProjectDescription(cProject, projectDescription);

					// Add a MOOSE Project Nature
					IProjectDescription desc = project.getDescription();
					String[] prevNatures = desc.getNatureIds();
					String[] newNatures = new String[prevNatures.length + 1];
					System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
					newNatures[prevNatures.length] = MooseNature.NATURE_ID;
					desc.setNatureIds(newNatures);
					project.setDescription(desc, new NullProgressMonitor());

				} catch (CoreException e) {
					logger.error(getClass().getName() + " Exception!", e);
					String errorMessage = "ICE could not import MOOSE as a C++ project.";
					return new Status(IStatus.ERROR, "org.eclipse.ice.client.widgets.moose", 1, errorMessage, null);
				}

				monitor.subTask("Importing into ICE.");
				monitor.worked(100);
				monitor.done();
				return Status.OK_STATUS;
			}

		};

		job.schedule();

		return null;
	}

	/**
	 * Private method used basically to just compartmentalize all the include
	 * additions for the MOOSE build system.
	 *
	 * @return
	 */
	private List<ICLanguageSettingEntry> getIncludePaths() {
		List<ICLanguageSettingEntry> includes = new ArrayList<ICLanguageSettingEntry>();

		includes.add(new CIncludePathEntry("/moose/framework/include/actions", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/auxkernels", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/base", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/bcs", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/constraints", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/dampers", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/dgkernels", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/dirackernels", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/executioners", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/functions", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/geomsearch", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/ics", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/indicators", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/kernels", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/markers", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/materials", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/mesh", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/meshmodifiers", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/multiapps", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/outputs", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/parser", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/postprocessors", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/preconditioners", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/predictors", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/restart", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/splits", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/timeintegrators", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(
				new CIncludePathEntry("/moose/framework/include/timesteppers", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/userobject", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/utils", ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/framework/include/vectorpostprocessors",
				ICSettingEntry.VALUE_WORKSPACE_PATH));
		includes.add(new CIncludePathEntry("/moose/libmesh/installed/include", ICSettingEntry.VALUE_WORKSPACE_PATH));

		return includes;
	}
}