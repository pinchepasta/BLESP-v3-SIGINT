#pragma once
#include <stdint.h>
#include <stddef.h>
#include <pthread.h>

typedef struct {
    uint8_t        *data;
    size_t          capacity;
    volatile size_t head;
    volatile size_t tail;
    pthread_mutex_t mutex;
    pthread_cond_t  not_empty;
} circbuff_t;

void circbuff_init(circbuff_t *b, size_t capacity);
void circbuff_free(circbuff_t *b);
void circbuff_clear(circbuff_t *b);
int  circbuff_push(circbuff_t *b, const uint8_t *src, size_t len);
int  circbuff_pop (circbuff_t *b,       uint8_t *dst, size_t len);
