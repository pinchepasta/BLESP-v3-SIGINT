#include "circbuff.h"
#include <stdlib.h>
#include <string.h>

void circbuff_init(circbuff_t *b, size_t capacity)
{
    b->data     = malloc(capacity);
    b->capacity = capacity;
    b->head     = 0;
    b->tail     = 0;
    pthread_mutex_init(&b->mutex, NULL);
    pthread_cond_init(&b->not_empty, NULL);
}

void circbuff_free(circbuff_t *b)
{
    pthread_mutex_lock(&b->mutex);
    if (b->data) {
        free(b->data);
        b->data = NULL;
    }
    pthread_mutex_unlock(&b->mutex);
    pthread_mutex_destroy(&b->mutex);
    pthread_cond_destroy(&b->not_empty);
}

void circbuff_clear(circbuff_t *b)
{
    if (!b || !b->data) return;
    pthread_mutex_lock(&b->mutex);
    b->head = 0;
    b->tail = 0;
    pthread_mutex_unlock(&b->mutex);
}

int circbuff_push(circbuff_t *b, const uint8_t *src, size_t len)
{
    if (!b->data) return -1;
    pthread_mutex_lock(&b->mutex);

    if (len > b->capacity) {
        src += (len - b->capacity);
        len = b->capacity;
    }

    size_t current_len = b->head - b->tail;
    if (len > (b->capacity - current_len)) {
        /* Overwrite oldest data */
        b->tail += (len - (b->capacity - current_len));
    }

    size_t offset = b->head % b->capacity;
    size_t first_part = b->capacity - offset;
    if (len < first_part) {
        memcpy(b->data + offset, src, len);
    } else {
        memcpy(b->data + offset, src, first_part);
        memcpy(b->data, src + first_part, len - first_part);
    }

    b->head += len;

    pthread_cond_signal(&b->not_empty);
    pthread_mutex_unlock(&b->mutex);
    return 0;
}

int circbuff_pop(circbuff_t *b, uint8_t *dst, size_t len)
{
    if (!b->data) return -1;
    pthread_mutex_lock(&b->mutex);

    /* Wait until enough data is available */
    while ((b->head - b->tail) < len) {
        /* We use a timeout to avoid hanging if the producer stops */
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_nsec += 100000000; /* 100ms */
        if (ts.tv_nsec >= 1000000000) {
            ts.tv_sec += 1;
            ts.tv_nsec -= 1000000000;
        }

        int rc = pthread_cond_timedwait(&b->not_empty, &b->mutex, &ts);
        if (rc != 0) {
            pthread_mutex_unlock(&b->mutex);
            return -1;
        }
    }

    size_t offset = b->tail % b->capacity;
    size_t first_part = b->capacity - offset;
    if (len < first_part) {
        memcpy(dst, b->data + offset, len);
    } else {
        memcpy(dst, b->data + offset, first_part);
        memcpy(dst + first_part, b->data, len - first_part);
    }

    b->tail += len;

    pthread_mutex_unlock(&b->mutex);
    return 0;
}
