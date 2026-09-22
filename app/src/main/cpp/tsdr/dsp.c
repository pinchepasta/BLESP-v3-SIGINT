#include "dsp.h"
#include <float.h>

#define LP_ALPHA 0.15f

void dsp_init(dsp_state_t *s)
{
    s->lp_state = 0.0f;
}

/* Single-pole IIR low-pass */
void dsp_lowpass(dsp_state_t *s, float *buf, int len)
{
    float state = s->lp_state;
    for (int i = 0; i < len; i++) {
        state = state + LP_ALPHA * (buf[i] - state);
        buf[i] = state;
    }
    s->lp_state = state;
}

void dsp_normalise(float *buf, int len, float *min_out, float *max_out)
{
    float mn =  FLT_MAX;
    float mx = -FLT_MAX;
    for (int i = 0; i < len; i++) {
        if (buf[i] < mn) mn = buf[i];
        if (buf[i] > mx) mx = buf[i];
    }
    float range = mx - mn;
    if (range < 1e-6f) range = 1.0f;
    for (int i = 0; i < len; i++)
        buf[i] = (buf[i] - mn) / range;
    if (min_out) *min_out = mn;
    if (max_out) *max_out = mx;
}
