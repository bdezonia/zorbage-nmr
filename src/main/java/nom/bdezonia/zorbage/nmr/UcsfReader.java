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
		int componentCount = 0;
		int fileVersion = 0;
		AxisHeader[] axisHeaders = new AxisHeader[4];  // all null
	}
	
	@SuppressWarnings("unused")
	private static class AxisHeader {
		
		String atomName = "";
		int    dataPtCount = 0;
		int    tileSize = 0;
		int    tileCount = 0;
		float  specFreq = 0;
		float  specWidth = 0;
		float  center = 0;
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
		
		if (info == null)
			
			return null;

		// populate dims from header info
		
		long[] dims = dimsFromHeader(info);
		
		if (dims.length == 0)
			
			return null;
		
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
			
			throw new IllegalArgumentException("unexpected component count "+info.componentCount);
		}

		DimensionedDataSource<U> data = DimensionedStorage.allocate(alg.construct(), dims);
		
		try {
		
			readNumbers(dis, info, alg, data);

		} catch (IOException e3) {

			System.out.println("IO EXCEPTION while reading numeric data! "+e3);
			
			return null;
		}
		
		MetaDataStore metadata = metadataFromHeader(info);
		
		if (metadata != null)
		
			data.metadata().merge(metadata);
		
		data.setSource(uri.toString());

		try {
			
			dis.close();

		} catch (Exception e4) {
			
			// ignore
		}
		
		return new Tuple2<>(alg, data);
	}
	
	private static
	
		HeaderInfo
		
			readHeader(DataInputStream dis)
	{
		try {
			
			String fileType = string(dis.readNBytes(10));
			
			if (fileType != "UCSF NMR") {
			
				System.out.println("input file is not a UCSF NMR data file");
				
				return null;
			}
			
			HeaderInfo info = new HeaderInfo();
			
			info.fileType = fileType;
			info.dimCount = dis.readByte();
			info.componentCount = dis.readByte();
			dis.readByte();
			info.fileVersion = dis.readByte();
			if (info.fileVersion != 2)
				System.out.println("Unexpected file version "+info.fileVersion);
			dis.readNBytes(166);
			
			if (info.dimCount > 0) {
				
				info.axisHeaders[0] = readAxisHeader(dis);
			}
			
			if (info.dimCount > 1) {
			
				info.axisHeaders[1] = readAxisHeader(dis);
			}
			
			if (info.dimCount > 2) {
			
				info.axisHeaders[2] = readAxisHeader(dis);
			}
			
			if (info.dimCount > 3) {
			
				info.axisHeaders[3] = readAxisHeader(dis);
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
		dis.readNBytes(2);
		axis.dataPtCount = dis.readInt();
		dis.readNBytes(4);
		axis.tileSize = dis.readInt();
		axis.specFreq = dis.readFloat();
		axis.specWidth = dis.readFloat();
		axis.center = dis.readFloat();
		dis.readNBytes(96);
		
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
		
		if (info.dimCount > 0) {
			
			xDim = info.axisHeaders[0].dataPtCount;
		}
		
		if (info.dimCount > 1) {
			
			yDim = info.axisHeaders[1].dataPtCount;
		}
		
		if (info.dimCount > 2) {
			
			zDim = info.axisHeaders[2].dataPtCount;
		}
		
		if (info.dimCount > 3) {
			
			aDim = info.axisHeaders[3].dataPtCount;
		}
				
		if (xDim > 0 && yDim > 0 && zDim > 0 && aDim > 0) {
			
			return new long[] {xDim, yDim, zDim, aDim};
		}

		if (xDim > 0 && yDim > 0 && zDim > 0) {
			
			return new long[] {xDim, yDim, zDim};
		}
		
		if (xDim > 0 && yDim > 0) {
			
			return new long[] {xDim, yDim};
		}
		
		if (xDim > 0) {
			
			return new long[] {xDim};
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

		int numD = info.dimCount;
		
		int xTileCount = 0;
		int yTileCount = 1;
		int zTileCount = 1;
		int aTileCount = 1;

		int xTileSize = 0;
		int yTileSize = 1;
		int zTileSize = 1;
		int aTileSize = 1;

		if (numD < 0 || numD > 4) {
				
			throw new IllegalArgumentException("Unexpected number of dimensions ("+numD+")");
		}

		else if (numD == 0) {
			
			// no initialization needed: already done
		}

		if (numD > 0) {
			
			xTileCount = info.axisHeaders[0].tileCount;
			xTileSize = info.axisHeaders[0].tileSize;
		}
		
		if (numD > 1) {
			
			yTileCount = info.axisHeaders[1].tileCount;
			yTileSize = info.axisHeaders[1].tileSize;
		}
		
		if (numD > 2) {
			
			zTileCount = info.axisHeaders[2].tileCount;
			zTileSize = info.axisHeaders[2].tileSize;
		}
		
		if (numD > 3) {
			
			aTileCount = info.axisHeaders[3].tileCount;
			aTileSize = info.axisHeaders[3].tileSize;
		}

		U value = alg.construct();
		
		float[] tmpFloats = new float[info.componentCount];

		float[] numbers = new float[info.componentCount * xTileSize * yTileSize * zTileSize * aTileSize];
		
		IntegerIndex pos = new IntegerIndex(numD);
		
		for (int xt = 0; xt < xTileCount; xt++) {

			int xOrigin = xt * xTileSize;
			
			for (int yt = 0; yt < yTileCount; yt++) {
				
				int yOrigin = yt * yTileSize;
				
				for (int zt = 0; zt < zTileCount; zt++) {
					
					int zOrigin = zt * zTileSize;
					
					for (int at = 0; at < aTileCount; at++) {

						int aOrigin = at * aTileSize;
						
						// read into tile structure

						for (int n = 0; n < numbers.length; n++) {
							
							numbers[n] = dis.readFloat();
						}

						int idx = 0;
						
						for (int xOff = 0; xOff < xTileSize; xOff++) {
							
							for (int yOff = 0; yOff < yTileSize; yOff++) {
								
								for (int zOff = 0; zOff < zTileSize; zOff++) {
									
									for (int aOff = 0; aOff < aTileSize; aOff++) {

										idx += info.componentCount;
										
										int x = xOrigin + xOff;
										int y = yOrigin + yOff;
										int z = zOrigin + zOff;
										int a = aOrigin + aOff;
										
										if (info.componentCount > 0) {
											
											pos.set(0,x);
										}
										
										if (info.componentCount > 1) {
											
											pos.set(1,y);
										}
										
										if (info.componentCount > 2) {
											
											pos.set(2,z);
										}
										
										if (info.componentCount > 3) {
											
											pos.set(3,a);
										}
										
										for (int i = 0; i < tmpFloats.length; i++) {
											
											tmpFloats[i] = numbers[idx + i];
										}

										value.setFromFloats(tmpFloats);
										
										data.set(pos, value);
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
	
		MetaDataStore
			
			metadataFromHeader(HeaderInfo info)
	{
		MetaDataStore metadata = new MetaDataStore();
		
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