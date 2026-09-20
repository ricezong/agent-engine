package cn.kong.engine.port.interaction;

import java.time.Duration;
import java.util.List;

import cn.kong.engine.msg.ToolCall;
import cn.kong.engine.msg.ToolSpec;

/**
 * 人机交互端口——"引擎阻塞等待人类回答"的统一语义。
 *
 * <p>提问（ask_question 工具）与门禁审批共用此通道：
 * 同步实现（控制台）直接阻塞；异步实现（HTTP 桥）park 住等待 engine.answer() 投递。
 */
public interface InteractionChannel {

    /** 提问并阻塞等待答案；超时/取消由实现处理。 */
    QuestionReply ask(Question question, Duration timeout);

    /** 破坏性工具的门禁审批。 */
    Decision gate(ToolCall call, ToolSpec spec);

    /** 异步通道投递答案（engine.answer 走这里）；同步通道返回 false。 */
    default boolean deliverAnswer(String questionId, String text) {
        return false;
    }

    /** 提问内容。 */
    record Question(String id, String text, List<String> options) {
        public Question {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** 提问的三种结局。 */
    sealed interface QuestionReply permits QuestionReply.Answered, QuestionReply.TimedOut, QuestionReply.Cancelled {

        record Answered(String text) implements QuestionReply {
        }

        record TimedOut() implements QuestionReply {
        }

        record Cancelled() implements QuestionReply {
        }
    }

    /** 门禁结论。 */
    enum Decision {
        APPROVED, REJECTED
    }
}
