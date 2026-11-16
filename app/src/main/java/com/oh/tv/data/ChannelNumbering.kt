package com.oh.tv.data

/** Preserve raw IPTV row positions when a provider omits a channel number. */
internal fun fallbackChannelNumber(batchStart: Int, index: Int): Int = batchStart + index + 1
