	.text
	movmu.l	r8,@-r15
	fmov	fr14,@-r15
	add	#-12,r15
	extu.b	r4,r4
	mov	r4,r0
	mov.l	1f,r1
	jmp	@r1
	nop
1:	.long	0x1C000924
