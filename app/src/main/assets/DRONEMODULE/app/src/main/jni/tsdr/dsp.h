#pragma once
#include <stdint.h>

typedef struct {
    float lp_state;   /* low-pass IIR state */
} dsp_state_t;

void dsp_init    (dsp_state_t *s);
void dsp_lowpass (dsp_state_t *s, float *buf, int len);
void dsp_normalise(float *buf, int len, float *min_out, float *max_out);
