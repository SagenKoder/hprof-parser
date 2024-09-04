/*
 * Copyright 2014 Edward Aftandilian. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.tufts.eaftan.hprofparser.handler;

import edu.tufts.eaftan.hprofparser.parser.datastructures.*;

/**
 * Primary interface to be used with the hprof parser.  The parser takes an implementation of
 * this interface and calls the matching callback method on each record encountered.
 * Implementations of this interface can do things like printing the record or building a graph.
 *
 * <p>You may assume that all references passed into the handler methods are non-null.
 *
 * <p>Generally you want to subclass {@code NullRecordHandler} rather than implement this interface
 * directly.
 */
public interface RecordHandler {

    void header(String format, int idSize, long time);

    void stringInUTF8(long id, String data);

    void loadClass(int classSerialNum, long classObjId, int stackTraceSerialNum,
                                   long classNameStringId);

    void unloadClass(int classSerialNum);

//    void stackFrame(long stackFrameId,
//                                    long methodNameStringId,
//                                    long methodSigStringId,
//                                    long sourceFileNameStringId,
//                                    int classSerialNum,
//                                    int location);

//    void stackTrace(int stackTraceSerialNum, int threadSerialNum, int numFrames,
//                                    long[] stackFrameIds);

//    void allocSites(short bitMaskFlags,
//                                    float cutoffRatio,
//                                    int totalLiveBytes,
//                                    int totalLiveInstances,
//                                    long totalBytesAllocated,
//                                    long totalInstancesAllocated,
//                                    AllocSite[] sites);

//    void heapSummary(int totalLiveBytes, int totalLiveInstances,
//                                     long totalBytesAllocated, long totalInstancesAllocated);

//    void startThread(int threadSerialNum,
//                                     long threadObjectId,
//                                     int stackTraceSerialNum,
//                                     long threadNameStringId,
//                                     long threadGroupNameId,
//                                     long threadParentGroupNameId);

//    void endThread(int threadSerialNum);

    void heapDump();

    void heapDumpEnd();

    void heapDumpSegment();

//    void cpuSamples(int totalNumOfSamples, CPUSample[] samples);

//    void controlSettings(int bitMaskFlags, short stackTraceDepth);

//    void rootUnknown(long objId);

//    void rootJNIGlobal(long objId, long JNIGlobalRefId);

//    void rootJNILocal(long objId, int threadSerialNum, int frameNum);

//    void rootJavaFrame(long objId, int threadSerialNum, int frameNum);

//    void rootNativeStack(long objId, int threadSerialNum);

//    void rootStickyClass(long objId);

//    void rootThreadBlock(long objId, int threadSerialNum);

//    void rootMonitorUsed(long objId);

//    void rootThreadObj(long objId, int threadSerialNum, int stackTraceSerialNum);

    void classDump(long classObjId,
                                   int stackTraceSerialNum,
                                   long superClassObjId,
                                   long classLoaderObjId,
                                   long signersObjId,
                                   long protectionDomainObjId,
                                   long reserved1,
                                   long reserved2,
                                   int instanceSize,
                                   Constant[] constants,
                                   Static[] statics,
                                   InstanceField[] instanceFields);

    void instanceDump(long objId, int stackTraceSerialNum, long classObjId,
                                      Value<?>[] instanceFieldValues);

    void objArrayDump(long objId, int stackTraceSerialNum, long elemClassObjId,
                                      long[] elems);

    void primArrayDump(long objId, int stackTraceSerialNum, byte elemType,
                                       Value<?>[] elems);

    void finished();

}
