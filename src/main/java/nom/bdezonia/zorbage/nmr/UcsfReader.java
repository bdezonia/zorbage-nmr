/*
 * zorbage-nmr: : code for populating NMR file data into zorbage structures for further processing
 *
 * Copyright (C) 2023 Barry DeZonia
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package nom.bdezonia.zorbage.nmr;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.Allocatable;
import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.algebra.SetFromFloats;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.data.DimensionedStorage;
import nom.bdezonia.zorbage.metadata.MetaDataStore;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.sampling.IntegerIndex;
import nom.bdezonia.zorbage.tuple.Tuple2;
import nom.bdezonia.zorbage.type.complex.float32.ComplexFloat32Member;
import nom.bdezonia.zorbage.type.octonion.float32.OctonionFloat32Member;
import nom.bdezonia.zorbage.type.quaternion.float32.QuaternionFloat32Member;
import nom.bdezonia.zorbage.type.real.float32.Float32Member;

/**
 * Read sparky UCSF files into zorbage structures.
 * 
 * @author Barry DeZonia
 */
public class UcsfReader {

	// do not instantiate
	
	private UcsfReader() { }

	@SuppressWarnings("unused")
	private static class HeaderInfo {

		String fileType = "";
		int dimCount = 0;
		int encoding = 0;
		int componentCount = 0;
		int fileVersion = 0;
		String owner = "";
		String date = "";
		String comment = "";
		AxisHeader[] axisHeaders = new AxisHeader[4];  // all null
	}
	
	private static class AxisHeader {
		
		String atomName = "";
		int    dataPtCount = 0;
		int    tileSize = 0;
		int    tileCount = 0;
		float  spectrometerFrequency = 0;
		float  spectralWidth = 0;
		float  transmitterOffset = 0;
	}
	
	// --- PUBLIC API ---
	
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DataBundle
	
			readAllDatasets(String filename)
	{
		try {
			
			URI uri = new URI("file", null, new File(filename).getAbsolutePath(), null);
			
			return readAllDatasets(uri);
	
		} catch (URISyntaxException e) {
			
			System.out.println("Bad name for file: "+e.getMessage());
			
			return new DataBundle();
		}
	}

	/**
	 * 
	 * @param uri
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Algebra<T,U>, U>
	
		DataBundle
	
			readAllDatasets(URI uri)
	{
		DataBundle bundle = new DataBundle();
		
		Tuple2<T,DimensionedDataSource<U>> result = 
				
			(Tuple2<T,DimensionedDataSource<U>>) (Object) readData(uri);

		if (result != null) {
			
			if (result.a() == G.FLT)
				
				bundle.flts.add((DimensionedDataSource<Float32Member>) result.b());	
			
			if (result.a() == G.CFLT)
				
				bundle.cflts.add((DimensionedDataSource<ComplexFloat32Member>) result.b());	
			
			if (result.a() == G.QFLT)
			
				bundle.qflts.add((DimensionedDataSource<QuaternionFloat32Member>) result.b());	
			
			if (result.a() == G.OFLT)
				
				bundle.oflts.add((DimensionedDataSource<OctonionFloat32Member>) result.b());	
		}
		
		return bundle;
	}

	// --- PRIVATE API ---

	@SuppressWarnings("unchecked")
	private static <T extends Algebra<T,U>, U extends Allocatable<U> & SetFromFloats>
	
		Tuple2<T,DimensionedDataSource<U>>
	
			readData(URI uri)
	{
		InputStream is = null;

		BufferedInputStream bis = null;

		DataInputStream dis = null;
		
		try {
			
			is = uri.toURL().openStream();

			bis = new BufferedInputStream(is);

			dis = new DataInputStream(bis);
		
		} catch (MalformedURLException e1) {
			
			System.out.println("BAD URI "+e1);
			
			return null;
			
		} catch (IOException e2) {
			
			System.out.println("IO EXCEPTION "+e2);
			
			return null;
		}
		
		HeaderInfo info = readHeader(dis);
		
		if (info == null) {
		
			try { dis.close(); } catch (Exception e) { ; }
			
			return null;
		}

		// populate dims from header info
		
		long[] dims = dimsFromHeader(info);
		
		if (dims.length == 0) {
			
			try { dis.close(); } catch (Exception e) { ; }
		
			return null;
		}

		T alg = null;
		
		if (info.componentCount <= 1) {

			alg = (T) G.FLT;
		}
		else if (info.componentCount <= 2) {
			
			alg = (T) G.CFLT;
		}
		else if (info.componentCount <= 4) {
			
			alg = (T) G.QFLT;
		}
		else if (info.componentCount <= 8) {
			
			alg = (T) G.OFLT;
		}
		else {
			
			try { dis.close(); } catch (Exception e) { ; }
			
			throw new IllegalArgumentException("unexpected component count "+info.componentCount);
		}

		DimensionedDataSource<U> data = DimensionedStorage.allocate(alg.construct(), dims);
		
		try {
		
			readNumbers(dis, info, alg, data);

		} catch (IOException e3) {

			System.out.println("IO EXCEPTION while reading numeric data! "+e3);
			
			try { dis.close(); } catch (Exception e) { ; }
			
			return null;
		}
		
		MetaDataStore metadata = metadataFromHeader(info);
		
		if (metadata != null)
		
			data.metadata().merge(metadata);
		
		data.setSource(uri.toString());
		
		data.setValueType("Amplitude");

		data.setValueUnit("");

		int xPos = xPos(info);
		int yPos = yPos(info);
		int zPos = zPos(info);
		int aPos = aPos(info);
		
		if (xPos >= 0) {
			
			data.setAxisType(xPos, info.axisHeaders[0].atomName);
			
			data.setAxisUnit(xPos, "ppm");
		}

		if (yPos >= 0) {
			
			data.setAxisType(yPos, info.axisHeaders[1].atomName);
			
			data.setAxisUnit(yPos, "ppm");
		}

		if (zPos >= 0) {
			
			data.setAxisType(zPos, info.axisHeaders[2].atomName);
			
			data.setAxisUnit(zPos, "ppm");
		}

		if (aPos >= 0) {
			
			data.setAxisType(aPos, info.axisHeaders[3].atomName);
			
			data.setAxisUnit(aPos, "ppm");
		}

		try { dis.close(); } catch (Exception e) { ; }
		
		return new Tuple2<>(alg, data);
	}
	
	private static
	
		HeaderInfo
		
			readHeader(DataInputStream dis)
	{
		try {
			
			String fileType = string(dis.readNBytes(10));
			
			if (!("UCSF NMR".equals(fileType))) {
			
				//System.out.println("input file is not a 'UCSF NMR' data file ("+fileType+")");
				
				return null;
			}
			
			HeaderInfo info = new HeaderInfo();
			
			info.fileType = fileType;
			info.dimCount = dis.readByte() & 0xff;
			info.componentCount = dis.readByte() & 0xff;
			info.encoding = dis.readByte() & 0xff;
			info.fileVersion = dis.readByte() & 0xff;
			if (info.fileVersion != 2)
				System.out.println("Unexpected file version "+info.fileVersion);
			info.owner = string(dis.readNBytes(9));
			info.date = string(dis.readNBytes(26));
			info.comment = string(dis.readNBytes(80));
			dis.readNBytes(51);

			// NOTE: It seems that axis headers are always defined as:
			//   X then Y then Z then A
			
			for (int i = 0; i < info.dimCount; i++) {
				
				info.axisHeaders[i] = readAxisHeader(dis);
			}
			
			return info;
			
		} catch (IOException e1) {
			
			return null;
		}
	}
	
	private static
	
		AxisHeader readAxisHeader(DataInputStream dis)
		
			throws IOException
	{
		AxisHeader axis = new AxisHeader();
	
		axis.atomName = string(dis.readNBytes(6));
		if (axis.atomName.equals(""))
			axis.atomName = "1H";
		dis.readNBytes(2);
		axis.dataPtCount = dis.readInt();
		dis.readNBytes(4);
		axis.tileSize = dis.readInt();
		axis.spectrometerFrequency = dis.readFloat();  // MHz
		axis.spectralWidth = dis.readFloat(); // Hz
		axis.transmitterOffset = dis.readFloat(); // ppm
		dis.readNBytes(96);
		
		axis.tileCount = (int) Math.ceil(((double) axis.dataPtCount) / axis.tileSize);
		
		return axis;
	}
	
	private static
	
		long[]
				
			dimsFromHeader(HeaderInfo info)
	{
		int xDim = 0;
		int yDim = 0;
		int zDim = 0;
		int aDim = 0;

		int xPos = xPos(info);
		int yPos = yPos(info);
		int zPos = zPos(info);
		int aPos = aPos(info);
		
		if (xPos >= 0) {
			
			xDim = info.axisHeaders[0].dataPtCount;
		}
		
		if (yPos >= 0) {
			
			yDim = info.axisHeaders[1].dataPtCount;
		}
		
		if (zPos >= 0) {
			
			zDim = info.axisHeaders[2].dataPtCount;
		}
		
		if (aPos >= 0) {
			
			aDim = info.axisHeaders[3].dataPtCount;
		}
				
		if (xDim > 0 && yDim > 0 && zDim > 0 && aDim > 0) {
			
			long[] dims = new long[4];
			dims[xPos] = xDim;
			dims[yPos] = yDim;
			dims[zPos] = zDim;
			dims[aPos] = aDim;
			return dims;
		}

		if (xDim > 0 && yDim > 0 && zDim > 0) {
			
			long[] dims = new long[3];
			dims[xPos] = xDim;
			dims[yPos] = yDim;
			dims[zPos] = zDim;
			return dims;
		}
		
		if (xDim > 0 && yDim > 0) {
			
			long[] dims = new long[2];
			dims[xPos] = xDim;
			dims[yPos] = yDim;
			return dims;
		}
		
		if (xDim > 0) {
			
			long[] dims = new long[2];
			dims[xPos] = xDim;
			return dims;
		}
		
		return new long[] {};
	}
	
	private static <T extends Algebra<T,U>, U extends SetFromFloats>
	
		void
		
			readNumbers(

				DataInputStream dis,
				HeaderInfo info,
				T alg,
				DimensionedDataSource<U> data
			)
			throws IOException
	{
		// read pixel data using header info

		int xTileCount = 1;
		int yTileCount = 1;
		int zTileCount = 1;
		int aTileCount = 1;

		int xTileSize = 1;
		int yTileSize = 1;
		int zTileSize = 1;
		int aTileSize = 1;

		int xPos = xPos(info);
		int yPos = yPos(info);
		int zPos = zPos(info);
		int aPos = aPos(info);
		
		if (info.dimCount <= 0 || info.dimCount > 4) {
				
			throw new IllegalArgumentException("Unexpected number of dimensions ("+info.dimCount+")");
		}

		if (xPos >= 0) {
			
			yTileCount = info.axisHeaders[0].tileCount;
			yTileSize = info.axisHeaders[0].tileSize;
		}

		if (yPos >= 0) {
			
			xTileCount = info.axisHeaders[1].tileCount;
			xTileSize = info.axisHeaders[1].tileSize;
		}

		if (zPos >= 0) {
			
			zTileCount = info.axisHeaders[2].tileCount;
			zTileSize = info.axisHeaders[2].tileSize;
		}

		if (aPos >= 0) {
			
			aTileCount = info.axisHeaders[3].tileCount;
			aTileSize = info.axisHeaders[3].tileSize;
		}

		U value = alg.construct();
		
		float[] tmpFloats = new float[info.componentCount];

		float[] numbers = new float[info.componentCount * xTileSize * yTileSize * zTileSize * aTileSize];
		
		IntegerIndex pos = new IntegerIndex(info.dimCount);
		
		// walk all the tiles
		
		for (int xt = 0; xt < xTileCount; xt++) {

			int xOrigin = xt * xTileSize;
			
			for (int yt = 0; yt < yTileCount; yt++) {
				
				int yOrigin = yt * yTileSize;
				
				for (int zt = 0; zt < zTileCount; zt++) {
					
					int zOrigin = zt * zTileSize;
					
					for (int at = 0; at < aTileCount; at++) {

						int aOrigin = at * aTileSize;
						
						// read a tile's worth of data into the tile structure

						for (int n = 0; n < numbers.length; n++) {
							
							numbers[n] = dis.readFloat();
						}

						// traverse the tile structure
						
						int idx = 0;
						
						for (int xOff = 0; xOff < xTileSize; xOff++) {
							
							for (int yOff = 0; yOff < yTileSize; yOff++) {
								
								for (int zOff = 0; zOff < zTileSize; zOff++) {
									
									for (int aOff = 0; aOff < aTileSize; aOff++) {

										// calc the real world coords of the point
										
										int x = xOrigin + xOff;
										int y = yOrigin + yOff;
										int z = zOrigin + zOff;
										int a = aOrigin + aOff;

										// make sure the point is not on the out of bounds edge of a tile
										
										boolean inBounds = true;
										
										if (xPos >= 0 && x >= info.axisHeaders[0].dataPtCount)
											
											inBounds = false;
										
										if (yPos >= 0  && y >= info.axisHeaders[1].dataPtCount)
											
											inBounds = false;
										
										if (zPos >= 0  && z >= info.axisHeaders[2].dataPtCount)
											
											inBounds = false;
										
										if (aPos >= 0  && a >= info.axisHeaders[3].dataPtCount)
										
											inBounds = false;
										
										if (inBounds) {

											// we have a good coordinate
											
											// set the position of the coordinate

											if (xPos >= 0) {
												
												if (xPos == 1)
													pos.set(xPos, info.axisHeaders[0].dataPtCount - x - 1);
												else
													pos.set(xPos, x);
											}
											
											if (yPos >= 0) {
												
												if (yPos == 1)
													pos.set(yPos, info.axisHeaders[1].dataPtCount - y - 1);
												else
													pos.set(yPos, y);
											}
											
											if (zPos >= 0) {
												
												if (zPos == 1)
													pos.set(zPos, info.axisHeaders[2].dataPtCount - z - 1);
												else
													pos.set(zPos, z);
											}
											
											if (aPos >= 0) {
												
												if (aPos == 1)
													pos.set(aPos, info.axisHeaders[3].dataPtCount - a - 1);
												else
													pos.set(aPos, a);
											}
											
											// gather the component values from the tile
											
											for (int i = 0; i < info.componentCount; i++) {
												
												tmpFloats[i] = numbers[idx + i];
											}

											// construct our U value from those floats
											
											value.setFromFloats(tmpFloats);
											
											// set the output data at the position to the U value
											
											data.set(pos, value);
										}
										
										// whether position was in bounds or not make sure to
										//   increment the position to the next set of relevant values.
										
										idx += info.componentCount;
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private static
	
		int xPos(HeaderInfo info)
	{
		return info.dimCount - 1;
	}

	private static
	
		int yPos(HeaderInfo info)
	{
		return info.dimCount - 2;
	}

	private static
	
		int zPos(HeaderInfo info)
	{
		return info.dimCount - 3;
	}

	private static
	
		int aPos(HeaderInfo info)
	{
		return info.dimCount - 4;
	}
	
	private static
	
		MetaDataStore
			
			metadataFromHeader(HeaderInfo info)
	{
		MetaDataStore metadata = new MetaDataStore();

		metadata.putString("owner", info.owner);
		metadata.putString("date", info.date);
		metadata.putString("comment", info.comment);

		for (int i = 0; i < info.dimCount; i++) {
			
			metadata.putString("axis "+i+" spectrometer frequency (MHz)", Float.toString(info.axisHeaders[i].spectrometerFrequency));
			metadata.putString("axis "+i+" spectral width (Hz)", Float.toString(info.axisHeaders[i].spectralWidth));
			metadata.putString("axis "+i+" transmitter offset (ppm)", Float.toString(info.axisHeaders[i].transmitterOffset));
		}
		
		return metadata;
	}
	
	private static String string(byte[] bytes) {
		
		String result = "";
		
		for (int i = 0; i < bytes.length; i++) {
			
			if (bytes[i] == 0)
				
				return result;
			
			result = result + (char) bytes[i];
		}
		
		return result;
	}
}
