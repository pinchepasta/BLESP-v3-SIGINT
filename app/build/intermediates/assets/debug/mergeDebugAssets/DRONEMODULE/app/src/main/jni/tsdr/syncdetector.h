#pragma once
#include <stdint.h>

typedef struct {
    float  *autocorr;
    int     size;
    int     peak_pos;
    float   peak_val;
} syncdetector_t;

void syncdetector_init     (syncdetector_t *s, int width);
void syncdetector_free     (syncdetector_t *s);
void syncdetector_push     (syncdetector_t *s, const float *line, int len);
void syncdetector_reset    (syncdetector_t *s);
int  syncdetector_get_autocorr(syncdetector_t *s, float *out, int *len);
