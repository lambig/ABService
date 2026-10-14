// GPL-2.0-or-later. Strict input and phase guards around streaming Chordino.
#include <vamp-hostsdk/PluginInputDomainAdapter.h>
#include <vamp-hostsdk/PluginBufferingAdapter.h>
#include "stream-core.h"
#include <cmath>
#include <cstring>
static StreamChordino* chord=nullptr;
static Vamp::HostExt::PluginBufferingAdapter* plugin=nullptr;
static float input[16384],block[1024];
static int blockFill=0,position=0,total=0,expected=0,phase=0;
static bool failed=false;
extern "C" {
void dispose(){delete plugin;plugin=nullptr;chord=nullptr;blockFill=position=total=expected=phase=0;failed=false;}
uintptr_t initialize(int samples){
 dispose();if(samples<1||samples>22050*14400)return 0;
 expected=samples;chord=new StreamChordino();auto* domain=new Vamp::HostExt::PluginInputDomainAdapter(chord);
 domain->setProcessTimestampMethod(Vamp::HostExt::PluginInputDomainAdapter::ShiftData);
 plugin=new Vamp::HostExt::PluginBufferingAdapter(domain);plugin->setPluginStepSize(1024);plugin->setPluginBlockSize(8192);
 if(!plugin->initialise(1,1024,1024)){dispose();return 0;}plugin->getOutputDescriptors();phase=1;return (uintptr_t)input;
}
static int runBlock(){const float* p=block;plugin->process(&p,Vamp::RealTime::frame2RealTime(position,22050));position+=1024;blockFill=0;return chord->failed?-1:0;}
int push(int count){
 if(!plugin||failed||phase<1||phase>2||count<1||count>16384||total+count>expected)return -1;
 for(int i=0;i<count;i++)if(!std::isfinite(input[i])||std::abs(input[i])>16){failed=true;return -2;}
 for(int i=0;i<count;i++){block[blockFill++]=input[i];if(blockFill==1024&&runBlock()){failed=true;return -3;}}
 total+=count;return 0;
}
int finish_pass(){
 if(!plugin||failed||phase<1||phase>2||total!=expected)return -1;
 if(blockFill){std::fill(block+blockFill,block+1024,0);if(runBlock()){failed=true;return -2;}}
 plugin->getRemainingFeatures();if(chord->failed||chord->frameCount()<1){failed=true;return -3;}
 if(phase==1){chord->lockTuning();phase=3;return chord->firstFrames;}
 if(chord->frames!=chord->firstFrames){failed=true;return -4;}phase=4;return chord->frames;
}
int begin_second(){if(!plugin||failed||phase!=3)return -1;plugin->reset();chord->secondPass();blockFill=position=total=0;phase=2;return 0;}
int last_state(){return plugin&&!failed&&phase==4?chord->lastState():-1;}
int state_count(){return chord?chord->stateCount():0;}
int frame_count(){return chord?chord->frameCount():0;}
uintptr_t label(int n){return chord?(uintptr_t)chord->label(n):0;}
uintptr_t tuning(){return chord?(uintptr_t)chord->tuning():0;}
double frame_time(int n){if(n<0)return -1;auto t=Vamp::RealTime::frame2RealTime(n*1024,22050);return t.sec+t.nsec/1e9;}
}
