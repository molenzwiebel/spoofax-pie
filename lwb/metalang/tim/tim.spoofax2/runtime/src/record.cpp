#include <map>
#include <string>
#include <iostream>
#include "gc.h"
#include "record.h"
#include "GarbageCollector.h"

using Record = std::map<std::string, int64_t>;

void* record_new(uint64_t pair_count, ...) {
    std::cerr << "RECORD_NEW" << std::endl;
    auto *record = static_cast<Record *>(garbageCollector.allocate(sizeof(Record), RECORD));
    new(record) Record;
    va_list args;
    va_start(args, pair_count);
    for (uint64_t i = 0; i < pair_count; i++) {
        const char *key = va_arg(args, const char*);
        int64_t value = va_arg(args, int64_t);
        (*record)[key] = value;
    }
    return record;
}

void record_write(void *record_ptr, const char *text, int64_t value) {
    Record &record = *static_cast<Record*>(record_ptr);
    record[text] = value;
}

int64_t record_read(void *record_ptr, const char *text) {
    auto &record = *static_cast<Record *>(record_ptr);
    auto search = record.find(text);
    if (search == record.end()) {
        printf("Invalid record read %s\n", text);
        exit(-1);
    }
    return search->second;
}

void record_write_ptr(void *record_ptr, const char *text, void* value) {
    Record &record = *static_cast<Record*>(record_ptr);
    record[text] = reinterpret_cast<int64_t>(value);
}

void* record_read_ptr(void *record_ptr, const char *text) {
    auto &record = *static_cast<Record *>(record_ptr);
    auto search = record.find(text);
    if (search == record.end()) {
        printf("Invalid record read %s\n", text);
        exit(-1);
    }
    return reinterpret_cast<void *>(search->second);
}

// TODO: Record destructor call
void record_delete(void *record_ptr) {
    auto &record = *static_cast<Record *>(record_ptr);
    record.~Record();
}