#include "syncdetector.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

void syncdetector_init(syncdetector_t *s, int width)
{
    s->size      = width;
    s->autocorr  = calloc(width, sizeof(float));
    s->peak_pos  = 0;
    s->peak_val  = 0.0f;
}

void syncdetector_free(syncdetector_t *s)
{
    free(s->autocorr);
    s->autocorr = NULL;
}

void syncdetector_reset(syncdetector_t *s)
{
    memset(s->autocorr, 0, s->size * sizeof(float));
    s->peak_pos = 0;
    s->peak_val = 0.0f;
}

/*
 * Push one line of magnitude samples and update running autocorrelation.
 * Uses an exponential moving average so recent lines matter more.
 */
void syncdetector_push(syncdetector_t *s, const float *line, int len)
{
    if (!s->autocorr || len == 0) return;
    int n = (len < s->size) ? len : s->size;

    float peak = 0.0f;
    int   peak_i = 0;

    for (int lag = 0; lag < n; lag++) {
        float corr = 0.0f;
        for (int i = 0; i < n - lag; i++)
            corr += line[i] * line[i + lag];
        corr /= (n - lag);

        /* EMA blend */
        s->autocorr[lag] = 0.9f * s->autocorr[lag] + 0.1f * corr;

        if (lag > 0 && s->autocorr[lag] > peak) {
            peak   = s->autocorr[lag];
            peak_i = lag;
        }
    }
    s->peak_pos = peak_i;
    s->peak_val = peak;
}

int syncdetector_get_autocorr(syncdetector_t *s, float *out, int *len)
{
    int n = (*len < s->size) ? *len : s->size;
    memcpy(out, s->autocorr, n * sizeof(float));
    *len = n;
    return 0;
}
