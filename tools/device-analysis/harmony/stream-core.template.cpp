// GPL-2.0-or-later. Streaming adaptation, 2026-10-14.
// Generated sections retain the operations/order of Chordino.cpp (QMUL/Matthias Mauch).
#include "stream-core.h"
#include "chromamethods.h"
#include <cmath>
#include <algorithm>
#include <cstdint>
__attribute__((import_module("path_store"),import_name("append"))) extern "C" int append_path(uintptr_t,int,int);
StreamChordino::StreamChordino():Chordino(22050){}
void StreamChordino::lockTuning(){
/* TUNING */
 lockedShift=intShift;lockedFraction=floatShift;firstFrames=m_frameCount;
}
void StreamChordino::secondPass(){phase=2;frames=0;previous.assign(m_chordnames.size(),0);current=previous;psi.assign(previous.size(),0);}
Vamp::Plugin::FeatureSet StreamChordino::process(const float*const* input,Vamp::RealTime timestamp){
 if(failed)return FeatureSet();
 NNLSBase::baseProcess(input,timestamp);
 if(timestamp!=Vamp::RealTime::frame2RealTime((m_frameCount-1)*1024,22050)||m_tuneLocal){failed=true;return FeatureSet();}
 if(phase==2)analyzeFrame();
 m_logSpectrum.clear();m_localTuning.clear();return FeatureSet();
}
void StreamChordino::analyzeFrame(){
 int intShift=lockedShift;float floatShift=lockedFraction;int nChord=m_chordnames.size();
/* FRAME */
/* CHROMA */
 advance(currentChordSalience);
}
void StreamChordino::advance(const std::vector<double>& obs){
 int nState=obs.size();double deltasum=0;const double selftransprob=.99;
 if(frames==0){
  for(int iState=0;iState<nState;++iState){current[iState]=(iState==nState-1?1.:0.)*obs[iState];deltasum+=current[iState];}
  for(int iState=0;iState<nState;++iState)current[iState]/=deltasum;
  std::fill(psi.begin(),psi.end(),0);
 }else{
  for(int jState=0;jState<nState;++jState){
   int bestState=nState-1;double bestValue=0;
   if(obs[jState]>0){for(int iState=0;iState<nState;++iState){
    double trans=iState==jState?selftransprob:(1-selftransprob)/(nState-1);
    double currentValue=previous[iState]*trans;
    if(currentValue>bestValue){bestValue=currentValue;bestState=iState;}
   }}
   current[jState]=bestValue*obs[jState];deltasum+=current[jState];psi[jState]=bestState;
  }
  if(deltasum>0){for(int iState=0;iState<nState;++iState)current[iState]/=deltasum;}
  else{for(int iState=0;iState<nState;++iState)current[iState]=1.0/nState;}
 }
 if(append_path((uintptr_t)psi.data(),nState,frames)!=0){failed=true;return;}
 previous.swap(current);frames++;
}
int StreamChordino::lastState()const{
 int state=previous.size()-1;double bestValue=0;
 for(int iState=0;iState<(int)previous.size();++iState){double currentValue=previous[iState];if(currentValue>bestValue){bestValue=currentValue;state=iState;}}
 return state;
}
