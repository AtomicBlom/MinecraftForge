package net.minecraftforge.common.model.animation;

import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.google.gson.annotations.SerializedName;
import net.minecraftforge.common.animation.Event;
import net.minecraftforge.common.animation.ITimeValue;
import net.minecraftforge.common.model.IModelState;
import org.apache.commons.lang3.tuple.Pair;

public abstract class AnimationStateMachineBase implements IAnimationStateMachine
{
    protected final ImmutableMap<String, ITimeValue> parameters;
    protected final ImmutableList<String> states;
    protected final ImmutableMultimap<String, String> transitions;
    @SerializedName("start_state")
    protected final String startState;

    private transient boolean shouldHandleSpecialEvents;
    private transient String currentStateName;
    protected transient float lastPollTime;

    protected AnimationStateMachineBase(ImmutableMap<String, ITimeValue> parameters, ImmutableList<String> states, ImmutableMultimap<String, String> transitions, String startState)
    {
        this.parameters = parameters;
        this.states = states;
        this.transitions = transitions;
        this.startState = startState;
    }

    protected static final class Args
    {
        private final ImmutableMap<String, ITimeValue> parameters;
        private final ImmutableList<String> states;
        private final ImmutableMultimap<String, String> transitions;
        private final String startState;

        public Args(ImmutableMap<String, ITimeValue> parameters, ImmutableList<String> states, ImmutableMultimap<String, String> transitions, String startState)
        {
            this.parameters = parameters;
            this.states = states;
            this.transitions = transitions;
            this.startState = startState;
        }
    }

    protected AnimationStateMachineBase(Args args)
    {
        this(args.parameters, args.states, args.transitions, args.startState);
    }

    /**
     * post-loading initialization hook.
     */
    protected void initialize()
    {
        shouldHandleSpecialEvents = true;
        lastPollTime = Float.NEGATIVE_INFINITY;
        currentStateName = startState;
    }

    public void transition(String newState)
    {
        if(!states.contains(newState))
        {
            throw new IllegalStateException("unknown state: " + newState);
        }
        if(!transitions.containsEntry(currentStateName, newState))
        {
            throw new IllegalArgumentException("no transition from current clip \"" + currentStateName + "\" to the clip \"" + newState + "\" found.");
        }
        currentStateName = newState;
        lastPollTime = Float.NEGATIVE_INFINITY;
    }

    public String currentState()
    {
        return currentStateName;
    }

    public void shouldHandleSpecialEvents(boolean value)
    {
        shouldHandleSpecialEvents = value;
    }

    protected Pair<IModelState, Iterable<Event>> filterSpecialEvents(Pair<IModelState, Iterable<Event>> pair)
    {
        boolean shouldFilter = false;
        if(shouldHandleSpecialEvents)
        {
            for(Event event : ImmutableList.copyOf(pair.getRight()).reverse())
            {
                if(event.event().startsWith("!"))
                {
                    shouldFilter = true;
                    if(event.event().startsWith("!transition:"))
                    {
                        String newState = event.event().substring("!transition:".length());
                        transition(newState);
                    }
                    else
                    {
                        System.out.println("Unknown special event \"" + event.event() + "\", ignoring");
                    }
                }
            }
        }
        if(!shouldFilter)
        {
            return pair;
        }
        return Pair.of(pair.getLeft(), Iterables.filter(pair.getRight(), new Predicate<Event>()
        {
            public boolean apply(Event event)
            {
                return !event.event().startsWith("!");
            }
        }));
    }
}
