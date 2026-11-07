package com.beautysense.app.v2

enum class Stage { HOME, ALIGN, ANALYZE, RESULTS, SELECT, GENERATE, LIVE }
data class Item(val categoryId:String,val productId:String,val variantId:String)
data class MirrorState(val stage:Stage=Stage.HOME,val items:Map<String,Item> = emptyMap(),val revision:Long=0,val epoch:Long=0) {
 val canGenerate get()=items.isNotEmpty()
 fun start()=copy(stage=Stage.ALIGN)
 fun select(item:Item):MirrorState { require(item.categoryId.isNotBlank() && item.productId.isNotBlank() && item.variantId.isNotBlank()); return copy(items=items + (item.categoryId to item), revision=revision+1,stage=Stage.SELECT) }
 fun remove(category:String)=copy(items=items-category,revision=revision+1,stage=Stage.SELECT)
 fun reset()=MirrorState(stage=Stage.ALIGN,epoch=epoch+1)
 fun accepts(e:Long,r:Long)=epoch==e && revision==r
}
