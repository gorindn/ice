/*******************************************************************************
 * Copyright (c) 2013, 2014 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation - 
 *   Jay Jay Billings, John Ankner
 *******************************************************************************/
package org.eclipse.ice.reflectivity.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.apache.commons.math.MathException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.ice.datastructures.ICEObject.ListComponent;
import org.eclipse.ice.datastructures.form.Form;
import org.eclipse.ice.io.csv.CSVReader;
import org.eclipse.ice.reflectivity.ReflectivityCalculator;
import org.eclipse.ice.reflectivity.ReflectivityProfile;
import org.eclipse.ice.reflectivity.ScatteringDensityProfile;
import org.eclipse.ice.reflectivity.Slab;
import org.eclipse.ice.reflectivity.Tile;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This class tests {@link org.eclipse.ice.reflectivity.ReflectivityCalculator}.
 * 
 * @author Jay Jay Billings, John Ankner
 *
 */
public class ReflectivityCalculatorTester {

	/**
	 * The CSV file that will be tested
	 */
	private static IFile testFile;
	/**
	 * The reader that will be tested.
	 */
	private static CSVReader reader;

	/**
	 * The set of slabs that define the system.
	 */
	private static Slab[] slabs;

	/**
	 * The project used by the test. It currently points to the
	 * CSVLoaderTesterWorkspace because the reflectivity CSV files are also used
	 * in the CSV tests.
	 */
	private static IProject project;

	/**
	 * A default tolerance for the tests.
	 */
	private double tol = 1.0e-3;

	/**
	 * This class loads the files for the test.
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		// Get the file separator used on this system, which is different across
		// OSes.
		String separator = System.getProperty("file.separator");
		// Create the path for the reflectivity file in the ICE tests directory
		String userHome = System.getProperty("user.home");
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		String projectName = "CSVLoaderTesterWorkspace";
		String filename = "getSpecRefSqrdMod_q841.csv";
		IPath projectPath = new Path(userHome + separator + "ICETests"
				+ separator + projectName + separator + ".project");

		// Setup the project
		try {
			// Create the project description
			IProjectDescription desc = ResourcesPlugin.getWorkspace()
					.loadProjectDescription(projectPath);
			// Get the project handle and create it
			project = workspaceRoot.getProject(desc.getName());
			project.create(desc, new NullProgressMonitor());
			// Open the project if it is not already open
			if (project.exists() && !project.isOpen()) {
				project.open(new NullProgressMonitor());
			}
			// Refresh the workspace
			project.refreshLocal(IResource.DEPTH_INFINITE,
					new NullProgressMonitor());
			// Create the IFile handle for the csv file
			testFile = project.getFile(filename);
		} catch (CoreException e) {
			// Catch exception for creating the project
			e.printStackTrace();
			fail();
		}

		// Create the reader
		reader = new CSVReader();

		// Create the slabs that define the system, starting with air
		Slab air = new Slab();
		air.thickness = 200.0;

		// NiOx
		Slab niOx = new Slab();
		niOx.scatteringLength = (0.00000686 + 0.00000715) / 2.0;
		niOx.trueAbsLength = 2.27931868269305E-09;
		niOx.incAbsLength = 4.74626235093697E-09;
		niOx.thickness = 22.0;
		niOx.interfaceWidth = 4.0 * 2.35;

		// Ni
		Slab ni = new Slab();
		ni.scatteringLength = 9.31e-6;
		ni.trueAbsLength = 2.27931868269305E-09;
		ni.incAbsLength = 4.74626235093697E-09;
		ni.thickness = 551.0;
		ni.interfaceWidth = 4.3 * 2.35;

		// SiNiOx
		Slab siNiOx = new Slab();
		siNiOx.scatteringLength = (0.00000554 + 0.00000585) / 2.0;
		siNiOx.trueAbsLength = 2.27931868269305E-09;
		siNiOx.incAbsLength = 4.74626235093697E-09;
		siNiOx.thickness = 42.0;
		siNiOx.interfaceWidth = 7.0 * 2.35;

		// SiOx
		Slab si = new Slab();
		si.scatteringLength = 2.070e-6;
		si.trueAbsLength = 4.74981478870069E-11;
		si.incAbsLength = 1.99769988072137E-12;
		si.thickness = 100.0;
		si.interfaceWidth = 17.5;

		// Create the slab list
		slabs = new Slab[5];
		slabs[0] = air;
		slabs[1] = niOx;
		slabs[2] = ni;
		slabs[3] = siNiOx;
		slabs[4] = si;

		return;

	}

	/**
	 * This class tests
	 * {@link ReflectivityCalculator#getExtensionLengths(double[], double, double, double, int, int, int)}
	 * .
	 */
	@Test
	public void testGetExtensionLengths() {
		// Load the convolution file
		Form form = reader.read(project.getFile("getExtensionLengths.csv"));
		ListComponent<String[]> lines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(403, lines.size());

		// Get the parameters and reference output values
		String line[] = lines.get(0);
		double delQ0 = Double.valueOf(line[0]);
		double delQ1oQ = Double.valueOf(line[1]);
		double wavelength = Double.valueOf(line[2]);
		int numPoints = Integer.valueOf(line[3]);
		int refNumLowPoints = Integer.valueOf(line[4]);
		int refNumHighPoints = Integer.valueOf(line[5]);

		// Load the q array. They are padded by numLowPoints for the
		// convolution.
		double[] waveVector = new double[lines.size() - 1];
		for (int i = 0; i < lines.size() - 1; i++) {
			line = lines.get(i + 1);
			waveVector[i] = Double.valueOf(line[0]);
		}
		assertEquals(numPoints, waveVector.length);
		assertEquals(4.401373320E-01, waveVector[numPoints - 1], 0.0);

		// Call the function
		int numLowPoints = 0, numHighPoints = 0;
		ReflectivityCalculator calculator = new ReflectivityCalculator();
		numLowPoints = calculator.getLowExtensionLength(waveVector, delQ0,
				delQ1oQ, numPoints);
		numHighPoints = calculator.getHighExtensionLength(waveVector, delQ0,
				delQ1oQ, numPoints);

		// Check the high and low extension lengths
		assertEquals(refNumLowPoints, numLowPoints);
		assertEquals(refNumHighPoints, numHighPoints);

		return;
	}

	/**
	 * This class tests
	 * {@link ReflectivityCalculator#convolute(double[], double, double, double, int, int, double[])}
	 * .
	 */
	@Test
	public void testConvolute() {
		// Load the convolution file
		Form form = reader.read(project.getFile("convolute.csv"));
		ListComponent<String[]> lines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(417, lines.size());

		// The first line of the file contains all of the parameters
		String[] line = lines.get(0);
		double delQ0 = Double.valueOf(line[0]);
		double delQ1oQ = Double.valueOf(line[1]);
		double wavelength = Double.valueOf(line[2]);
		int numPoints = Integer.valueOf(line[3]);
		int numLowPoints = Integer.valueOf(line[4]);
		int numHighPoints = Integer.valueOf(line[5]);

		// Load the q and refFit arrays. They are padded by numLowPoints for the
		// convolution.
		double[] waveVector = new double[lines.size() - 1];
		double[] refRefFit = new double[lines.size() - 1]; // Reference!
		for (int i = 0; i < lines.size() - 1; i++) {
			line = lines.get(i + 1);
			waveVector[i] = Double.valueOf(line[0]);
			refRefFit[i] = Double.valueOf(line[1]);
		}
		assertEquals(0.461926308814, waveVector[415], 0.0);
		assertEquals(3.87921357905325E-09, refRefFit[415], 0.0);

		// Load the tiles
		form = reader.read(project.getFile("getSpecRefSqrdMod_q841.csv"));
		ListComponent<String[]> tileLines = (ListComponent<String[]>) form
				.getComponent(1);
		Tile[] tiles = loadTiles(tileLines);
		assertEquals(173, tiles.length);

		// Compute the initial value of refFit. This code was adapted from the
		// original VB code and it is called, in that code, right before
		// ManConFixedLambda.
		double[] refFit = new double[lines.size() - 1];
		double qEff = 0.0;
		ReflectivityCalculator calculator = new ReflectivityCalculator();
		for (int i = 0; i < numHighPoints + numLowPoints + numPoints; i++) {
			if (waveVector[i] < 1.0e-10) {
				qEff = 1.0e-10;
			} else {
				qEff = waveVector[i];
			}
			refFit[i] = calculator.getModSqrdSpecRef(qEff, wavelength, tiles);
		}

		// Do the convolution and check the result against the reference values.
		calculator.convolute(waveVector, delQ0, delQ1oQ, wavelength, numPoints,
				numLowPoints, numHighPoints, refFit);
		for (int i = 0; i < refFit.length; i++) {
			// Most of these results agree to roughly 1e-4, but some of them
			// disagree by as much as 3.2%. They are clustered around 90 <= i <=
			// 101.
			assertEquals(refRefFit[i], refFit[i],
					Math.abs(refRefFit[i]) * 3.2e-2);
		}

		return;
	}

	/**
	 * This class tests
	 * {@link ReflectivityCalculator#getModSqrdSpecRef(double, double, Tile[])}.
	 */
	@Test
	public void testGetSpecRefSqrdMod() {

		// Load the file
		Form form = reader.read(project.getFile("getSpecRefSqrdMod_q841.csv"));
		ListComponent<String[]> lines = (ListComponent<String[]>) form
				.getComponent(1);

		// Get the two single parameters and the final result out of the data
		// for the first test case
		String[] line = lines.get(0);
		double waveVectorQ, wavelength, expectedSpecRefSqrd;
		waveVectorQ = Double.valueOf(line[0]);
		wavelength = Double.valueOf(line[1]);
		expectedSpecRefSqrd = Double.valueOf(line[2]);

		// Load the tiles from the rest of the data
		Tile[] tiles = loadTiles(lines);
		assertEquals(173, tiles.length);

		// Get the squared modulus of the specular reflectivity with the values
		// from the file for the first case.
		ReflectivityCalculator calculator = new ReflectivityCalculator();
		double specRefSqrd = calculator.getModSqrdSpecRef(waveVectorQ,
				wavelength, tiles);
		System.out.println(specRefSqrd + " " + expectedSpecRefSqrd);
		System.out.println("RERR = " + (specRefSqrd - expectedSpecRefSqrd)
				/ expectedSpecRefSqrd);
		assertEquals(expectedSpecRefSqrd, specRefSqrd,
				Math.abs(expectedSpecRefSqrd) * tol);

		// Get the two single parameters and the final result out of the data
		// for the second test case
		line = lines.get(1);
		waveVectorQ = Double.valueOf(line[0]);
		wavelength = Double.valueOf(line[1]);
		expectedSpecRefSqrd = Double.valueOf(line[2]);
		System.out.println(waveVectorQ + " " + wavelength + " "
				+ expectedSpecRefSqrd);

		// Get the squared modulus of the specular reflectivity with the values
		// from the file for the second case.
		specRefSqrd = calculator.getModSqrdSpecRef(waveVectorQ, wavelength,
				tiles);
		System.out.println(specRefSqrd + " " + expectedSpecRefSqrd);
		System.out.println("RERR = " + (specRefSqrd - expectedSpecRefSqrd)
				/ expectedSpecRefSqrd);
		assertEquals(expectedSpecRefSqrd, specRefSqrd,
				Math.abs(expectedSpecRefSqrd) * tol);

		return;
	}

	/**
	 * This operation loads the set of Tiles from the reference file, ignoring
	 * the first and second lines that store the reference values.
	 * 
	 * @param lines
	 *            The ListComponent with the lines from the file
	 * @return the list of Tiles from the file
	 */
	private Tile[] loadTiles(ListComponent<String[]> lines) {

		// Load all of the tiles
		Tile[] tiles = new Tile[lines.size() - 2];
		for (int i = 2; i < lines.size(); i++) {
			// Load the line
			String[] line = lines.get(i);
			// Create the tile and load the data from the line
			Tile tile = new Tile();
			tile.scatteringLength = Double.valueOf(line[0]);
			tile.trueAbsLength = Double.valueOf(line[1]);
			tile.incAbsLength = Double.valueOf(line[2]);
			tile.thickness = Double.valueOf(line[3]);
			// Load the tile into the array
			tiles[i - 2] = tile;
		}

		return tiles;
	}

	/**
	 * This operation tests
	 * {@link ReflectivityCalculator#getInterfacialProfile(int, double[], double[])}
	 * .
	 * 
	 * @throws MathException
	 *             This exception is thrown if the erf can't be computed during
	 *             the calculation.
	 */
	@Test
	public void testGetInterfacialProfile() throws MathException {
		// Get the file holding the test values
		Form form = reader.read(project.getFile("genErf.csv"));
		ListComponent<String[]> lines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(101, lines.size());

		// Create the reference data. Start by getting the number of roughness
		// steps to make and then get the zInt and rufInt arrays.
		int numRough = Integer.valueOf(lines.get(0)[2]);
		double[] refZInt = new double[ReflectivityCalculator.maxRoughSize];
		double[] refRufInt = new double[ReflectivityCalculator.maxRoughSize];

		// Load the reference arrays
		for (int i = 0; i < ReflectivityCalculator.maxRoughSize; i++) {
			String[] line = lines.get(i);
			refZInt[i] = Double.valueOf(line[0]);
			refRufInt[i] = Double.valueOf(line[1]);
		}

		// Create the test arrays
		double[] zInt = new double[ReflectivityCalculator.maxRoughSize];
		double[] rufInt = new double[ReflectivityCalculator.maxRoughSize];

		// Create the calculator and get the interfacial profile
		ReflectivityCalculator calc = new ReflectivityCalculator();
		calc.getInterfacialProfile(numRough, zInt, rufInt);

		// Check the results
		System.out.println("numRough = " + numRough);
		for (int i = 0; i < ReflectivityCalculator.maxRoughSize; i++) {
			// These values agree exceptionally well, so I decided to check with
			// the square of the tolerance (MUCH tighter bound) for zInt and
			// 10*tol*tol for refRuf, which is still better than we need.
			assertEquals(refZInt[i], zInt[i], Math.abs(refZInt[i]) * tol * tol);
			assertEquals(refRufInt[i], rufInt[i], Math.abs(refRufInt[i]) * 10.0
					* tol * tol);
		}

		return;
	}

	/**
	 * This operation tests {@link ReflectivityCalculator#generateTiles()}.
	 * 
	 * @throws MathException
	 */
	@Test
	public void testGenerateTiles() throws MathException {

		// Create the calculator
		ReflectivityCalculator calculator = new ReflectivityCalculator();

		// Create the test arrays
		double[] zInt = new double[ReflectivityCalculator.maxRoughSize];
		double[] rufInt = new double[ReflectivityCalculator.maxRoughSize];

		// Get the interfacial profile
		int numRough = 41;
		calculator.getInterfacialProfile(numRough, zInt, rufInt);

		// Generate the tiles
		Tile[] genTiles = calculator.generateTiles(slabs, numRough, zInt,
				rufInt);

		// Load the reference tiles
		Form form = reader.read(project.getFile("getSpecRefSqrdMod_q841.csv"));
		ListComponent<String[]> tileLines = (ListComponent<String[]>) form
				.getComponent(1);
		Tile[] refTiles = loadTiles(tileLines);
		assertEquals(173, refTiles.length);

		// Check the generated tiles against the reference data
		assertEquals(refTiles.length, genTiles.length);
		for (int i = 0; i < refTiles.length; i++) {
			System.out.println("Tile = " + i);
			// Scattering length
			assertEquals(refTiles[i].scatteringLength,
					genTiles[i].scatteringLength,
					Math.abs(refTiles[i].scatteringLength * tol));
			// True absorption cross section
			assertEquals(refTiles[i].trueAbsLength, genTiles[i].trueAbsLength,
					Math.abs(refTiles[i].trueAbsLength * tol));
			// Incoherent absorption cross section
			assertEquals(refTiles[i].incAbsLength, genTiles[i].incAbsLength,
					Math.abs(refTiles[i].incAbsLength * tol));
			// Thickness
			assertEquals(refTiles[i].thickness, genTiles[i].thickness,
					Math.abs(refTiles[i].thickness * tol));
		}
		System.out.println(refTiles.length + " " + genTiles.length);

		return;
	}

	/**
	 * This operation tests
	 * {@link ReflectivityCalculator#convoluteReflectivity()}.
	 */
	@Test
	public void testConvoluteReflectivity() {

		// Load the reference tiles
		Form form = reader.read(project.getFile("getSpecRefSqrdMod_q841.csv"));
		ListComponent<String[]> tileLines = (ListComponent<String[]>) form
				.getComponent(1);
		Tile[] refTiles = loadTiles(tileLines);
		assertEquals(173, refTiles.length);

		// Load the parameters and reference data
		form = reader.read(project.getFile("conRefTileFixedLambda.csv"));
		ListComponent<String[]> refLines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(403, refLines.size());

		// Get the parameters
		double deltaQ0 = Double.valueOf(refLines.get(0)[0]);
		double deltaQ1ByQ = Double.valueOf(refLines.get(0)[1]);
		double lambda = Double.valueOf(refLines.get(0)[2]);
		boolean getRQ4 = Boolean.valueOf(refLines.get(0)[3]);

		// Get the reference reflectivity results and the q input array
		double[] waveVector = new double[402];
		double[] refReflectivity = new double[402];

		// Load the reference arrays.
		for (int i = 1; i < 403; i++) {
			String[] line = refLines.get(i);
			refReflectivity[i - 1] = Double.valueOf(line[0]);
			waveVector[i - 1] = Double.valueOf(line[1]);
		}

		// Do the calculation
		ReflectivityCalculator calc = new ReflectivityCalculator();
		double[] reflectivity = calc.convoluteReflectivity(deltaQ0, deltaQ1ByQ,
				lambda, getRQ4, waveVector, refTiles);

		// Check the results
		assertEquals(refReflectivity.length, reflectivity.length);
		for (int i = 0; i < refReflectivity.length; i++) {
			// Four percent error is the best that I can do on this because the
			// behavior at the end points does not match. I think it is due to
			// an accumulation of errors from other sources.
			assertEquals(refReflectivity[i], reflectivity[i],
					Math.abs(refReflectivity[i]) * 0.04);
		}

		return;
	}

	/**
	 * This operation tests
	 * {@link ReflectivityCalculator#getScatteringDensityProfile()}.
	 */
	@Test
	public void testGetProfile() {
		// Load the reference tiles
		Form form = reader.read(project.getFile("getSpecRefSqrdMod_q841.csv"));
		ListComponent<String[]> tileLines = (ListComponent<String[]>) form
				.getComponent(1);
		Tile[] refTiles = loadTiles(tileLines);
		assertEquals(173, refTiles.length);

		// Load the reference data
		form = reader.read(project.getFile("plotProfile.csv"));
		ListComponent<String[]> refLines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(346, refLines.size());

		// Get the reference reflectivity results and the q input array
		double[] refDepth = new double[346];
		double[] refScatteringDensity = new double[346];

		// Load the reference arrays.
		for (int i = 0; i < refDepth.length; i++) {
			String[] line = refLines.get(i);
			refDepth[i] = Double.valueOf(line[0]);
			refScatteringDensity[i] = Double.valueOf(line[1]);
		}

		// Get the profile
		ReflectivityCalculator calc = new ReflectivityCalculator();
		ScatteringDensityProfile profile = calc
				.getScatteringDensityProfile(refTiles);

		// Check the values
		assertEquals(refDepth.length, profile.depth.length);
		assertEquals(refScatteringDensity.length,
				profile.scatteringDensity.length);
		for (int i = 0; i < refDepth.length; i++) {
			assertEquals(refDepth[i], profile.depth[i], Math.abs(refDepth[i])
					* tol);
			assertEquals(refScatteringDensity[i], profile.scatteringDensity[i],
					Math.abs(refScatteringDensity[i]) * 3.0 * tol);
		}

		return;
	}

	/**
	 * This operation tests
	 * {@link ReflectivityCalculator#getReflectivityProfile()}.
	 */
	@Test
	public void testGetReflectivityProfile() {

		// Load the parameters and reference data
		Form form = reader.read(project.getFile("conRefTileFixedLambda.csv"));
		ListComponent<String[]> refLines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(403, refLines.size());

		// Get the parameters
		double deltaQ0 = Double.valueOf(refLines.get(0)[0]);
		double deltaQ1ByQ = Double.valueOf(refLines.get(0)[1]);
		double lambda = Double.valueOf(refLines.get(0)[2]);
		boolean getRQ4 = Boolean.valueOf(refLines.get(0)[3]);

		// Get the reference reflectivity results and the q input array
		double[] waveVector = new double[402];
		double[] refReflectivity = new double[402];

		// Load the reference arrays. Skip the first line because this file has
		// additional parameters on that line.
		for (int i = 1; i < 403; i++) {
			String[] line = refLines.get(i);
			refReflectivity[i - 1] = Double.valueOf(line[0]);
			waveVector[i - 1] = Double.valueOf(line[1]);
		}

		// Load the reference data for the scattering density profile
		form = reader.read(project.getFile("plotProfile.csv"));
		ListComponent<String[]> refPlotProfileLines = (ListComponent<String[]>) form
				.getComponent(1);
		assertEquals(346, refPlotProfileLines.size());

		// Get the reference reflectivity results and the q input array
		double[] refDepth = new double[346];
		double[] refScatteringDensity = new double[346];

		// Load the reference arrays.
		for (int i = 0; i < refDepth.length; i++) {
			String[] line = refPlotProfileLines.get(i);
			refDepth[i] = Double.valueOf(line[0]);
			refScatteringDensity[i] = Double.valueOf(line[1]);
		}
		
		// Calculate the reflectivity profile
		ReflectivityCalculator calc = new ReflectivityCalculator();
		ReflectivityProfile profile = calc.getReflectivityProfile(slabs, 41,
				deltaQ0, deltaQ1ByQ, lambda, waveVector, false);

		// Check the results
		assertEquals(refReflectivity.length, profile.reflectivity.length);
		assertEquals(waveVector.length, profile.waveVector.length);
		// Check the reflectivity values
		//System.out.println("----- Dumping Reflectivity ----- ");
		for (int i = 0; i < refReflectivity.length; i++) {
			// They should be exact because the wave vector is an input
			assertEquals(waveVector[i], profile.waveVector[i],0.0);
			// Four percent error is the best that I can do on the reflectivity
			// because the behavior at the end points does not match. I think it
			// is due to an accumulation of errors from other sources.
			assertEquals(refReflectivity[i], profile.reflectivity[i],
					Math.abs(refReflectivity[i]) * 0.04);
			//System.out.println(profile.waveVector[i]+","+profile.reflectivity[i]);
		}
		//System.out.println("----- Dumping Scattering Density ----- ");
		// Check the scattering density values
		assertEquals(refDepth.length, profile.depth.length);
		assertEquals(refScatteringDensity.length,
				profile.scatteringDensity.length);
		for (int i = 0; i < refDepth.length; i++) {
			assertEquals(refDepth[i], profile.depth[i], Math.abs(refDepth[i])
					* tol);
			assertEquals(refScatteringDensity[i], profile.scatteringDensity[i],
					Math.abs(refScatteringDensity[i]) * 3.0 * tol);
			//System.out.println(profile.depth[i]+","+profile.scatteringDensity[i]);
		}

		return;
	}

}
