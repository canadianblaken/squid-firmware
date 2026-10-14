	.text
	mov.l	1f,r1
	jmp	@r1
	nop
1:	.long	0x1C045610
