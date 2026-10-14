// GPL-2.0-or-later. Bounded two-pass Chordino adaptation.
#include "Chordino.h"
#include <cstdint>
class StreamChordino:public Chordino{
public:
 StreamChordino();
 FeatureSet process(const float*const*,Vamp::RealTime) override;
 FeatureSet getRemainingFeatures() override{return FeatureSet();}
 void lockTuning();void secondPass();int lastState()const;
 int stateCount()const{return m_chordnames.size();}
 const char* label(int n)const{return n>=0&&n<stateCount()?m_chordnames[n].c_str():nullptr;}
 int frameCount()const{return m_frameCount;}
 const float* tuning()const{return m_meanTunings.data();}
 bool failed=false;int phase=1,frames=0,firstFrames=0;
private:
 void analyzeFrame();void advance(const std::vector<double>&);
 int lockedShift=0;float lockedFraction=0;bool clipwarned=false;
 std::vector<double> previous,current;std::vector<uint16_t> psi;
};
