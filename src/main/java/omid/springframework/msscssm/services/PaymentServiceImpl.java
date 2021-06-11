package omid.springframework.msscssm.services;

import lombok.RequiredArgsConstructor;
import omid.springframework.msscssm.domain.Payment;
import omid.springframework.msscssm.domain.PaymentEvent;
import omid.springframework.msscssm.domain.PaymentState;
import omid.springframework.msscssm.repository.PaymentRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    public static final String PAYMENT_ID_HEADER = "payment_id";
    private final PaymentRepository paymentRepository;
    private final StateMachineFactory<PaymentState,PaymentEvent> stateMachineFactory;
    private final PaymentStateChangeInterceptor paymentStateChangeInterceptor;
    @Override
    public Payment newPayment(Payment payment) {
        payment.setPaymentState(PaymentState.NEW);
        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public StateMachine<PaymentState, PaymentEvent> preAuth(Long paymentId) {
        StateMachine<PaymentState, PaymentEvent> sm = build(paymentId);
        sendEvent(paymentId,sm,PaymentEvent.PRE_AUTHORIZED);
        return sm;
    }

    @Override
    @Transactional
    public StateMachine<PaymentState, PaymentEvent> authorizePayment(Long paymentId) {
        StateMachine<PaymentState, PaymentEvent> sm = build(paymentId);
        sendEvent(paymentId,sm,PaymentEvent.AUTH_PROVED);

        return sm;
    }

    @Override
    @Transactional
    public StateMachine<PaymentState, PaymentEvent> declineAuth(Long paymentId) {
        StateMachine<PaymentState, PaymentEvent> sm = build(paymentId);
        sendEvent(paymentId,sm,PaymentEvent.PRE_AUTH_DECLINED);
        return sm;
    }

    private void sendEvent(Long paymentId,StateMachine<PaymentState,PaymentEvent> sm,PaymentEvent paymentEvent){
        Message msg = MessageBuilder.withPayload(paymentEvent)
            .setHeader(PAYMENT_ID_HEADER,paymentId)
            .build();
        sm.sendEvent(msg);
    }

    private StateMachine<PaymentState,PaymentEvent> build(Long paymentId){
        Payment payment = paymentRepository.getOne(paymentId);
        StateMachine<PaymentState, PaymentEvent> sm = stateMachineFactory.getStateMachine(Long.toString(payment.getId()));
        sm.stop();
        sm.getStateMachineAccessor()
                .doWithAllRegions(sma->{
                    sma.addStateMachineInterceptor(paymentStateChangeInterceptor);
                    sma.resetStateMachine(new DefaultStateMachineContext<>(payment.getPaymentState(),null,null,null));
                });
        sm.start();
        return sm;
    }
}
